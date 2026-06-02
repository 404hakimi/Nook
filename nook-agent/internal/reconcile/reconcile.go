// Package reconcile: frontline agent 周期拉 backend 期望态, 跟本地 xray 实际 diff 后收敛.
//
// 模型: backend 只写 DB, agent 负责把远端 xray 拉平到 DB 期望 (desired-state reconcile).
// 缺则补 (adu/ado/adrules), 多则删 (rmu/rmo/rmrules); 只动业务前缀 (out_/rule_), 不碰静态出站 / 内置规则.
package reconcile

import (
	"context"
	"log"
	"strings"
	"time"

	"nook-agent/internal/client"
	"nook-agent/internal/xray"
)

const (
	desiredPath       = "/api/agent/reconcile/desired"
	sharedInboundTag  = "in_shared" // 跟后端 XrayConstants.SHARED_INBOUND_TAG 对齐
	outboundTagPrefix = "out_"
	ruleTagPrefix     = "rule_"
)

// DesiredClient 跟后端 XrayReconcileClientDTO 对齐.
type DesiredClient struct {
	ClientEmail string `json:"clientEmail"`
	ClientUUID  string `json:"clientUuid"`
	InboundTag  string `json:"inboundTag"`
	OutboundTag string `json:"outboundTag"`
	RuleTag     string `json:"ruleTag"`
	AduJSON     string `json:"aduJson"`
	AdoJSON     string `json:"adoJson"`
	AdrulesJSON string `json:"adrulesJson"`
}

// Reconciler 周期把本地 xray 拉平到 backend 期望态.
type Reconciler struct {
	cli      *client.Client
	xc       *xray.Client
	interval time.Duration
}

func New(cli *client.Client, xc *xray.Client, interval time.Duration) *Reconciler {
	return &Reconciler{cli: cli, xc: xc, interval: interval}
}

// Run: 启动先收敛一次, 之后每 interval 一次; ctx 取消退出.
func (r *Reconciler) Run(ctx context.Context) {
	if r.interval <= 0 {
		log.Printf("[reconcile] interval<=0, 不启动")
		return
	}
	log.Printf("[reconcile] 启动, interval=%v", r.interval)
	t := time.NewTicker(r.interval)
	defer t.Stop()
	r.once(ctx)
	for {
		select {
		case <-ctx.Done():
			log.Printf("[reconcile] 退出")
			return
		case <-t.C:
			r.once(ctx)
		}
	}
}

func (r *Reconciler) once(ctx context.Context) {
	var desired []DesiredClient
	if err := r.cli.Get(desiredPath, &desired); err != nil {
		log.Printf("[reconcile] 拉期望态失败, 跳过本轮: %v", err)
		return
	}
	r.apply(ctx, desired)
}

func (r *Reconciler) apply(ctx context.Context, desired []DesiredClient) {
	// 实读本地; 任一失败放弃本轮 (拿空集会误删全部)
	actualUsers, err := r.xc.ListUsers(ctx, sharedInboundTag) // email→uuid
	if err != nil {
		log.Printf("[reconcile] 读本地 users 失败, 跳过本轮: %v", err)
		return
	}
	actualOutbounds, err := r.xc.ListOutboundTags(ctx)
	if err != nil {
		log.Printf("[reconcile] 读本地 outbounds 失败, 跳过本轮: %v", err)
		return
	}
	actualRules, err := r.xc.ListRuleTags(ctx)
	if err != nil {
		log.Printf("[reconcile] 读本地 rules 失败, 跳过本轮: %v", err)
		return
	}
	actualOutSet := toSet(actualOutbounds)
	actualRuleSet := toSet(actualRules)

	desiredEmails := make(map[string]bool, len(desired))
	desiredOutbounds := make(map[string]bool, len(desired))
	desiredRules := make(map[string]bool, len(desired))

	// 缺则补; email 在但 UUID 变了(rotate) 则摘旧装新 (email 不变, 流量统计不断档)
	for _, d := range desired {
		desiredEmails[d.ClientEmail] = true
		desiredOutbounds[d.OutboundTag] = true
		desiredRules[d.RuleTag] = true
		curUUID, exists := actualUsers[d.ClientEmail]
		switch {
		case !exists:
			if err := r.xc.AddUser(ctx, d.AduJSON); err != nil {
				log.Printf("[reconcile] +user %s 失败: %v", d.ClientEmail, err)
			} else {
				log.Printf("[reconcile] +user %s", d.ClientEmail)
			}
		case curUUID != d.ClientUUID:
			if err := r.xc.RemoveUser(ctx, sharedInboundTag, d.ClientEmail); err != nil {
				log.Printf("[reconcile] ~user %s 摘旧失败: %v", d.ClientEmail, err)
			} else if err := r.xc.AddUser(ctx, d.AduJSON); err != nil {
				log.Printf("[reconcile] ~user %s 装新失败: %v", d.ClientEmail, err)
			} else {
				log.Printf("[reconcile] ~user %s UUID 轮换", d.ClientEmail)
			}
		}
		if !actualOutSet[d.OutboundTag] {
			if err := r.xc.AddOutbound(ctx, d.AdoJSON); err != nil {
				log.Printf("[reconcile] +outbound %s 失败: %v", d.OutboundTag, err)
			} else {
				log.Printf("[reconcile] +outbound %s", d.OutboundTag)
			}
		}
		if !actualRuleSet[d.RuleTag] {
			if err := r.xc.AddRules(ctx, d.AdrulesJSON); err != nil {
				log.Printf("[reconcile] +rule %s 失败: %v", d.RuleTag, err)
			} else {
				log.Printf("[reconcile] +rule %s", d.RuleTag)
			}
		}
	}

	// 多则删 (仅业务前缀, 不碰静态出站 / 内置 api 规则)
	for email := range actualUsers {
		if !desiredEmails[email] {
			if err := r.xc.RemoveUser(ctx, sharedInboundTag, email); err != nil {
				log.Printf("[reconcile] -user %s 失败: %v", email, err)
			} else {
				log.Printf("[reconcile] -user %s", email)
			}
		}
	}
	for _, tag := range actualOutbounds {
		if strings.HasPrefix(tag, outboundTagPrefix) && !desiredOutbounds[tag] {
			if err := r.xc.RemoveOutbound(ctx, tag); err != nil {
				log.Printf("[reconcile] -outbound %s 失败: %v", tag, err)
			} else {
				log.Printf("[reconcile] -outbound %s", tag)
			}
		}
	}
	for _, tag := range actualRules {
		if strings.HasPrefix(tag, ruleTagPrefix) && !desiredRules[tag] {
			if err := r.xc.RemoveRule(ctx, tag); err != nil {
				log.Printf("[reconcile] -rule %s 失败: %v", tag, err)
			} else {
				log.Printf("[reconcile] -rule %s", tag)
			}
		}
	}
}

func toSet(ss []string) map[string]bool {
	m := make(map[string]bool, len(ss))
	for _, s := range ss {
		m[s] = true
	}
	return m
}
