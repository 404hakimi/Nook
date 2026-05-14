package com.nook.biz.node.handler.xray.client;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientReplayReportRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.cli.XrayInboundCli;
import com.nook.biz.node.framework.xray.cli.XrayOutboundCli;
import com.nook.biz.node.framework.xray.cli.XrayStatsCli;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.service.xray.slot.XraySlotPoolService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.operation.api.OpProgressSink;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Xray Client 操作执行体 (op queue handler 调本类干活).
 *
 * <p>从 XrayClientServiceImpl 拆出来; 业务对外入队接口仍在 service, 真正干 SSH+DB 的逻辑全在这里.
 * package-private 锁住跨包绕队列调用, service 包根本看不到这些方法.
 *
 * @author nook
 */
@Slf4j
@Component
public class ClientOpExecutor {

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayClientTrafficMapper xrayClientTrafficMapper;
    @Resource
    private XrayInboundCli inboundCli;
    @Resource
    private XrayOutboundCli outboundCli;
    @Resource
    private XrayStatsCli statsCli;
    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private XraySlotPoolService slotPoolService;
    @Resource
    private ResourceIpPoolService resourceIpPoolService;
    @Resource
    private XrayClientValidator clientValidator;
    @Resource
    private XrayDaemonProbe xrayDaemonProbe;

    /** CLIENT_PROVISION 实际执行体. */
    @Transactional(rollbackFor = Exception.class)
    XrayClientDO doProvision(XrayClientProvisionReqVO reqVO, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        // 业务校验
        sink.report("校验业务参数", 15);
        clientValidator.validateForProvision(reqVO);
        // IP 唯一约束
        clientValidator.validateIpNotInUse(reqVO.getIpId());

        // 加载 server xray 节点配置
        sink.report("加载节点信息", 25);
        XrayNodeDO node = xrayNodeService.getXrayNode(reqVO.getServerId());

        // 原子占用落地 IP, 同事务回滚自动归还
        sink.report("占用落地 IP", 35);
        ResourceIpPoolDO ipEntry = resourceIpPoolService.occupyById(reqVO.getIpId(), reqVO.getMemberUserId());
        if (StrUtil.isBlank(ipEntry.getIpAddress()) || ObjectUtil.isNull(ipEntry.getSocks5Port())) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    reqVO.getIpId(), "落地 IP 的 SOCKS5 凭据未配置");
        }

        // 分配 client id + slot
        sink.report("分配 slot 与端口", 45);
        String clientId = UUID.randomUUID().toString().replace("-", "");
        int slotIndex = slotPoolService.allocateSlot(reqVO.getServerId(), clientId);
        int listenPort = node.getSlotPortBase() + slotIndex;
        String inboundTag = formatSlotTag("in_slot_", slotIndex);
        String outboundTag = formatSlotTag("out_slot_", slotIndex);
        String clientUuid = UUID.randomUUID().toString();
        String clientEmail = "member_" + reqVO.getMemberUserId() + "_" + reqVO.getIpId();

        InboundUserSpec userSpec = InboundUserSpec.builder()
                .externalInboundRef(inboundTag)
                .email(clientEmail)
                .uuid(clientUuid)
                .protocol(reqVO.getProtocol())
                .flow(StrUtil.blankToDefault(reqVO.getFlow(), ""))
                .totalBytes(reqVO.getTotalBytes() == null ? 0L : reqVO.getTotalBytes())
                .expiryEpochMillis(reqVO.getExpiryEpochMillis() == null ? 0L : reqVO.getExpiryEpochMillis())
                .limitIp(reqVO.getLimitIp() == null ? 0 : reqVO.getLimitIp())
                .build();

        // DB 先落库: DB 是权威源, 远端 xray 是 DB 的派生; 失败由 @Transactional 回滚 slot/IP/Client 三表;
        // 并发同 IP 时由 DB UNIQUE 直接拦在调远端之前, 不留远端孤儿 inbound
        sink.report("DB 落库", 55);
        XrayClientDO entity = XrayClientDO.builder()
                .id(clientId)
                .serverId(reqVO.getServerId())
                .ipId(reqVO.getIpId())
                .memberUserId(reqVO.getMemberUserId())
                .slotIndex(slotIndex)
                .externalInboundRef(inboundTag)
                .protocol(reqVO.getProtocol())
                .transport(reqVO.getTransport())
                .listenIp(reqVO.getListenIp())
                .listenPort(listenPort)
                .clientUuid(clientUuid)
                .clientEmail(clientEmail)
                .status(1)
                .build();
        try {
            xrayClientMapper.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException dke) {
            throw new BusinessException(XrayErrorCode.CLIENT_IP_ALREADY_USED, reqVO.getIpId());
        }

        int apiPort = node.getXrayApiPort();
        // 整个 provision 链路 acquire 一次, 所有 CLI 复用同一 session
        sink.report("建立 SSH 会话", 65);
        SshSession session = SshSessions.acquire(reqVO.getServerId(), SshSessionScope.SHARED);
        // 防御性清零 stats counter, 让同 email 残留 (吊销失败 / 直删 DB 等) 路径下新 client 也从 0 起算
        try {
            statsCli.readUserTraffic(session, apiPort, clientEmail, true);
        } catch (Exception ignore) { }

        // 远端非事务, 用旗标精确回滚已完成的步骤; 抛异常时同事务的 DB 三表会一起回滚
        boolean inboundAdded = false;
        boolean freedomRemoved = false;
        boolean socksAdded = false;
        try {
            sink.report("注册 inbound", 75);
            inboundCli.addInbound(session, apiPort, inboundTag, listenPort, userSpec);
            inboundAdded = true;

            // 替换占位 freedom 为真实 socks; 删-加间隙 < 200ms, 期间漏到默认 outbound
            sink.report("替换 outbound 占位", 90);
            outboundCli.removeOutbound(session, apiPort, outboundTag);
            freedomRemoved = true;

            outboundCli.addSocksOutbound(session, apiPort, outboundTag,
                    ipEntry.getIpAddress(), ipEntry.getSocks5Port(),
                    ipEntry.getSocks5Username(), ipEntry.getSocks5Password());
            socksAdded = true;
        } catch (RuntimeException e) {
            log.error("[provision] CLI 失败 server={} slot={} email={} stage=[inbound={} freedomRemoved={} socks={}]",
                    reqVO.getServerId(), slotIndex, clientEmail, inboundAdded, freedomRemoved, socksAdded, e);
            if (socksAdded) {
                try { outboundCli.removeOutbound(session, apiPort, outboundTag); }
                catch (Exception ignore) { }
            }
            if (freedomRemoved) {
                try { outboundCli.addFreedomOutbound(session, apiPort, outboundTag); }
                catch (Exception ignore) { }
            }
            if (inboundAdded) {
                try { inboundCli.removeInbound(session, apiPort, inboundTag); }
                catch (Exception ignore) { }
            }
            throw e;
        }
        log.info("[provision] OK server={} slot={} port={} email={} ip={}",
                reqVO.getServerId(), slotIndex, listenPort, clientEmail, ipEntry.getIpAddress());
        return entity;
    }

    /** CLIENT_REVOKE 实际执行体. */
    @Transactional(rollbackFor = Exception.class)
    void doRevoke(String inboundEntityId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("加载客户端记录", 20);
        XrayClientDO e = clientValidator.validateExists(inboundEntityId);
        String inboundTag = e.getExternalInboundRef();
        String outboundTag = formatSlotTag("out_slot_", e.getSlotIndex());
        int apiPort = xrayNodeService.getXrayNode(e.getServerId()).getXrayApiPort();
        sink.report("建立 SSH 会话", 35);
        SshSession session = SshSessions.acquire(e.getServerId(), SshSessionScope.SHARED);

        // 远端清理: 删 inbound (该客户连接断, 但其他客户不受影响)
        sink.report("删除远端 inbound", 50);
        try {
            inboundCli.removeInbound(session, apiPort, inboundTag);
        } catch (BusinessException be) {
            // 远端已不存在视为成功, 目标态本来就是没了
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
            log.warn("[revoke] inbound 已不存在 server={} tag={}", e.getServerId(), inboundTag);
        }

        // 删真实 socks5 outbound + 加回占位 freedom (保持 routing rule 引用不空)
        sink.report("回收 outbound 占位", 65);
        try {
            outboundCli.removeOutbound(session, apiPort, outboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        try {
            outboundCli.addFreedomOutbound(session, apiPort, outboundTag);
        } catch (RuntimeException re) {
            // 占位还原失败: 不阻塞 revoke 主流程, 仅 warn (routing rule 此时引用不存在的 outbound, 流量走 default)
            log.warn("[revoke] 占位 freedom 还原失败 server={} tag={}, 由后续巡检修复",
                    e.getServerId(), outboundTag, re);
        }

        // DB 硬删 + slot 释放; 流量累计行也一并清掉, 避免 client_id 死引用孤儿
        sink.report("DB 删除与 slot 释放", 80);
        xrayClientMapper.deleteById(e.getId());
        xrayClientTrafficMapper.deleteByClientId(e.getId());
        slotPoolService.releaseSlot(e.getServerId(), e.getSlotIndex());

        // 退订落地 IP: occupied → cooling, 等冷却到期由 sweep 任务回到 available;
        // 走 try 是因为 IP 行可能已被运维手动删 / 状态错位, 不阻断 revoke 主流程
        sink.report("落地 IP 退订", 92);
        try {
            resourceIpPoolService.releaseToCooling(e.getIpId());
        } catch (RuntimeException re) {
            log.warn("[revoke] IP 退订失败 server={} ipId={}, 需运维手动处理 IP 状态: {}",
                    e.getServerId(), e.getIpId(), re.getMessage());
        }

        log.info("[revoke] OK server={} slot={} email={}",
                e.getServerId(), e.getSlotIndex(), e.getClientEmail());
    }

    /** CLIENT_ROTATE 实际执行体: 换 UUID, 端口/tag 不变, 重建 inbound 让新 UUID 生效. */
    XrayClientDO doRotate(String inboundEntityId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("加载客户端记录", 20);
        XrayClientDO e = clientValidator.validateExists(inboundEntityId);
        String inboundTag = e.getExternalInboundRef();
        String newUuid = UUID.randomUUID().toString();
        int apiPort = xrayNodeService.getXrayNode(e.getServerId()).getXrayApiPort();
        sink.report("建立 SSH 会话", 35);
        SshSession session = SshSessions.acquire(e.getServerId(), SshSessionScope.SHARED);

        // 删 inbound + 同 tag/port 重建 (1:1 模型每 inbound 1 user, rotate 走 inbound 重建)
        sink.report("删除旧 inbound", 50);
        try {
            inboundCli.removeInbound(session, apiPort, inboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }

        InboundUserSpec spec = InboundUserSpec.builder()
                .externalInboundRef(inboundTag)
                .email(e.getClientEmail())
                .uuid(newUuid)
                .protocol(e.getProtocol())
                .build();
        sink.report("注册新 inbound", 70);
        try {
            inboundCli.addInbound(session, apiPort, inboundTag, e.getListenPort(), spec);
        } catch (RuntimeException addErr) {
            // 重建失败: 标 status=3 待巡检 — 客户连不上, 需要人工 / 后续 reconciler 介入
            log.error("[rotate] del 后 add 失败 server={} email={}, 标 status=3",
                    e.getServerId(), e.getClientEmail(), addErr);
            xrayClientMapper.updateStatus(e.getId(), 3, LocalDateTime.now());
            throw addErr;
        }

        sink.report("更新 DB UUID", 90);
        xrayClientMapper.updateClientUuid(e.getId(), newUuid);
        e.setClientUuid(newUuid);
        log.info("[rotate] OK server={} slot={} email={} 新 UUID 已生效",
                e.getServerId(), e.getSlotIndex(), e.getClientEmail());
        return e;
    }

    /** CLIENT_SYNC 实际执行体. */
    void doSyncOne(String clientId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        XrayClientDO c = clientValidator.validateExists(clientId);
        sink.report("加载节点配置", 20);
        XrayNodeDO node = xrayNodeService.getXrayNode(c.getServerId());
        sink.report("建立 SSH 会话", 30);
        SshSession session = SshSessions.acquire(c.getServerId(), SshSessionScope.RECONCILE);
        sink.report("加载落地 IP 凭据", 40);
        ResourceIpPoolDO ipEntry = resourceIpPoolService.getIpPool(c.getIpId());
        syncSingle(session, node.getXrayApiPort(), c, ipEntry, sink);
    }

    /** SERVER_REPLAY 实际执行体. */
    XrayClientReplayReportRespVO doReplayServer(String serverId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("加载节点配置", 15);
        XrayNodeDO node = xrayNodeService.getXrayNode(serverId);
        sink.report("建立 SSH 会话", 25);
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.RECONCILE);
        return replayInternal(session, node, sink);
    }

    /** SERVER_RECONCILE 实际执行体: 探 xray uptime, 重启过才 replay. */
    void doReplayIfRestarted(String serverId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("加载节点", 20);
        XrayNodeDO node = xrayNodeService.getXrayNode(serverId);
        SshSession session;
        try {
            sink.report("建立 SSH 会话", 35);
            session = SshSessions.acquire(serverId, SshSessionScope.RECONCILE);
        } catch (RuntimeException e) {
            log.warn("[reconciler] SSH 不通 server={}, 本轮跳过: {}", serverId, e.getMessage());
            return;
        }
        sink.report("探测 xray uptime", 50);
        Optional<Instant> currentUptime = xrayDaemonProbe.readUptime(session);
        if (currentUptime.isEmpty()) {
            // xray 没起 / unit 缺失; 不能判断重启, 跳过, 等下一轮
            return;
        }
        Instant cur = currentUptime.get();
        Instant last = node.getLastXrayUptime() == null
                ? null : node.getLastXrayUptime().toInstant(ZoneOffset.UTC);
        // last 为 null (首次) 也视为重启; xray 装好后第一轮自动 replay 一次把 DB 推全
        if (last != null && !cur.isAfter(last)) return;

        log.info("[reconciler] xray 重启检测 server={} prev={} now={}, 触发 replay", serverId, last, cur);
        sink.report("xray 重启检测, 触发 replay", 60);
        replayInternal(session, node, sink);
        xrayNodeService.markReplayDone(serverId, LocalDateTime.ofInstant(cur, ZoneOffset.UTC));
    }

    /**
     * replay 核心: 先 lsi 拿远端 inbound tag, 仅推 "DB 有但远端缺" 的 client, 已对齐的跳过避免断连;
     * reconciler 调度 / 手动全量 replay 共用. 一次 acquire 一次拉表, 一次 lsi.
     * progress 在每轮 client 同步前后推百分比, 整体框定在 30-95 区间.
     */
    private XrayClientReplayReportRespVO replayInternal(SshSession session, XrayNodeDO node, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        String serverId = node.getServerId();
        int apiPort = node.getXrayApiPort();

        sink.report("拉取 DB 目标列表", 35);
        List<XrayClientDO> targets = new ArrayList<>();
        for (XrayClientDO c : xrayClientMapper.selectByServerId(serverId)) {
            if (c.getStatus() != null && c.getStatus() == 2) continue;
            targets.add(c);
        }

        // 拉远端 inbound tag set (1 次 SSH); 远端已存在的 inbound 视为客户在用, 跳过避免无谓断连.
        // 1:1 模型 outbound tag 跟 inbound tag 一一对应 + provision 时同步替换, 所以 inbound 在 = 整套 OK.
        sink.report("拉取远端 inbound 列表", 45);
        Set<String> remoteTags = new HashSet<>(inboundCli.listInbounds(session, apiPort));

        List<XrayClientDO> needSync = new ArrayList<>();
        for (XrayClientDO c : targets) {
            String tag = c.getExternalInboundRef();
            if (StrUtil.isNotBlank(tag) && !remoteTags.contains(tag)) {
                needSync.add(c);
            }
        }

        // 仅对 needSync 准备 IP 凭据; 已对齐的不查省 DB 调用
        sink.report("加载 IP 凭据", 55);
        Map<String, ResourceIpPoolDO> ipMap = new HashMap<>(needSync.size() * 2);
        for (XrayClientDO c : needSync) {
            String ipId = c.getIpId();
            if (StrUtil.isBlank(ipId) || ipMap.containsKey(ipId)) continue;
            try {
                ipMap.put(ipId, resourceIpPoolService.getIpPool(ipId));
            } catch (Exception ex) {
                ipMap.put(ipId, null);
            }
        }

        int success = 0;
        List<String> failed = new ArrayList<>();
        int total = needSync.size();
        int idx = 0;
        for (XrayClientDO c : needSync) {
            idx++;
            // 把 needSync 循环映射到 60-95 区间, 让进度条平滑 (n=0 时跳过区间)
            if (total > 0) {
                int pct = 60 + (35 * idx) / total;
                sink.report("同步 client " + idx + "/" + total, Math.min(95, pct));
            }
            try {
                syncSingle(session, apiPort, c, ipMap.get(c.getIpId()), OpProgressSink.noop());
                success++;
            } catch (Exception ex) {
                log.error("[reconciler] sync 失败 client={} email={}: {}",
                        c.getId(), c.getClientEmail(), ex.getMessage());
                failed.add(c.getId());
            }
        }

        int alreadyOk = targets.size() - needSync.size();
        XrayClientReplayReportRespVO report = new XrayClientReplayReportRespVO();
        report.setServerId(serverId);
        report.setTotalCount(targets.size());
        report.setAlreadyOkCount(alreadyOk);
        report.setSuccessCount(success);
        report.setFailedClientIds(failed);
        log.info("[reconciler] replay server={} total={} alreadyOk={} synced={} failed={}",
                serverId, targets.size(), alreadyOk, success, failed.size());
        return report;
    }

    /**
     * 单 client 同步到远端 (幂等 remove → add); 入参全部 caller 预查好, 内部仅调 CLI + 1 次 status 写回.
     * progress 写中间步骤百分比; replay 循环里传 noop 不打扰 UI.
     */
    private void syncSingle(SshSession session, int apiPort, XrayClientDO c,
                            ResourceIpPoolDO ipEntry, OpProgressSink progress) {
        if (ipEntry == null || StrUtil.isBlank(ipEntry.getIpAddress()) || ObjectUtil.isNull(ipEntry.getSocks5Port())) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    c.getIpId(), "落地 IP 凭据丢失, 无法 sync");
        }

        String inboundTag = c.getExternalInboundRef();
        String outboundTag = formatSlotTag("out_slot_", c.getSlotIndex());

        // inbound 重建 (幂等)
        progress.report("删除旧 inbound", 55);
        try {
            inboundCli.removeInbound(session, apiPort, inboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        progress.report("注册新 inbound", 70);
        InboundUserSpec spec = InboundUserSpec.builder()
                .externalInboundRef(inboundTag)
                .email(c.getClientEmail())
                .uuid(c.getClientUuid())
                .protocol(c.getProtocol())
                .build();
        try {
            inboundCli.addInbound(session, apiPort, inboundTag, c.getListenPort(), spec);
        } catch (RuntimeException addErr) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw addErr;
        }

        // outbound 重建 (幂等)
        progress.report("删除旧 outbound", 80);
        try {
            outboundCli.removeOutbound(session, apiPort, outboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        progress.report("注册新 socks outbound", 90);
        try {
            outboundCli.addSocksOutbound(session, apiPort, outboundTag,
                    ipEntry.getIpAddress(), ipEntry.getSocks5Port(),
                    ipEntry.getSocks5Username(), ipEntry.getSocks5Password());
        } catch (RuntimeException addErr) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw addErr;
        }

        xrayClientMapper.updateStatus(c.getId(), 1, LocalDateTime.now());
        progress.report("DB 状态回写", 95);
    }

    /** 把 slot 编号格式化成 "in_slot_05" / "out_slot_05" 这种 2 位补 0 字符串. */
    private static String formatSlotTag(String prefix, int slotIndex) {
        return prefix + String.format("%02d", slotIndex);
    }
}
