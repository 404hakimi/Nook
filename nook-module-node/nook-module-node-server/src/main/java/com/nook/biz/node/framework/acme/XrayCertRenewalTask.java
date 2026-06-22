package com.nook.biz.node.framework.acme;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.framework.agent.AgentControlClient;
import com.nook.biz.node.framework.xray.install.XrayCertPushRequest;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.biz.system.api.domain.DomainUtils;
import com.nook.biz.system.api.domain.SystemDomainApi;
import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * TLS 证书续期任务: 定时扫临期证书 → {@link XrayCertManager} 重签并落库 → 推新证书给 agent 写盘 + reload xray.
 * <p>取代旧 on-server acme.sh 的自动续期 (reloadcmd). 单实例假设; 多实例需加分布式锁 (Redisson 已在 classpath).
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayCertRenewalTask {

    /** 剩余 < 此天数即续 (= 复用阈值; 与 XrayCertManager 同源, 保证扫到即重签). */
    @Value("${nook.acme.renew-before-days:30}")
    private int renewBeforeDays;

    /** 推证书到 agent 的超时秒数. */
    @Value("${nook.acme.cert-push-timeout-seconds:120}")
    private int pushTimeoutSeconds;

    @Resource
    private XrayInstallService xrayInstallService;
    @Resource
    private SystemDomainApi systemDomainApi;
    @Resource
    private XrayCertManager xrayCertManager;
    @Resource
    private AgentControlClient agentControlClient;
    @Resource
    private ResourceServerService resourceServerService;

    /** 默认每天 03:30 跑一次; cron 可配. */
    @Scheduled(cron = "${nook.acme.renew-cron:0 30 3 * * ?}")
    public void renewExpiring() {
        LocalDateTime threshold = LocalDateTime.now().plusDays(renewBeforeDays);
        List<XrayInstallDO> due = xrayInstallService.listRenewable(threshold);
        if (due.isEmpty()) {
            return;
        }
        log.info("[acme-renew] 扫到 {} 张临期证书 (剩余 < {} 天), 开始续期", due.size(), renewBeforeDays);
        int ok = 0;
        for (XrayInstallDO row : due) {
            try {
                renewOne(row);
                ok++;
            } catch (Exception e) {
                // 单台失败不阻断其它; 本轮漏的下轮再扫 (仍在窗口内)
                log.error("[acme-renew] server={} 续期失败: {}", row.getServerId(), e.getMessage(), e);
            }
        }
        log.info("[acme-renew] 续期完成 {}/{}", ok, due.size());
    }

    private void renewOne(XrayInstallDO row) {
        String serverId = row.getServerId();
        if (StrUtil.isBlank(row.getDomainId())) {
            log.warn("[acme-renew] server={} 有证书却无 domainId, 跳过", serverId);
            return;
        }
        SystemDomainRespDTO domain = systemDomainApi.getById(row.getDomainId());
        String fqdn = DomainUtils.buildFqdn(row.getSubdomain(), domain.getDomain());

        // 重签 (扫到即在窗口内 → ensureCert 必重签) + 落库; 复用 XrayCertManager 的 per-fqdn 单飞锁, 与安装路径互斥
        IssuedCert cert = xrayCertManager.ensureCert(serverId, fqdn, domain.getCfApiToken(), null);

        ResourceServerDO srv = resourceServerService.getServerMap(Set.of(serverId)).get(serverId);
        if (srv == null) {
            log.warn("[acme-renew] server={} 已重签落库, 但 resource_server 缺失, 无法推送 agent", serverId);
            return;
        }
        agentControlClient.pushCert(srv.getIpAddress(), srv.getAgentToken(),
                XrayCertPushRequest.builder()
                        .serverId(serverId)
                        .domain(fqdn)
                        .tlsCertPem(cert.getCertPem())
                        .tlsKeyPem(cert.getKeyPem())
                        .timeoutSeconds(pushTimeoutSeconds)
                        .build(),
                line -> log.info("[acme-renew] {} | {}", serverId, line.strip()));
        log.info("[acme-renew] server={} fqdn={} 续期完成 (新到期 {})", serverId, fqdn, cert.getNotAfter());
    }
}
