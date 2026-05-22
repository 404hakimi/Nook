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
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.XrayConstants;

import static com.nook.biz.node.framework.xray.XrayConstants.outboundTagOf;
import static com.nook.biz.node.framework.xray.XrayConstants.ruleTagOf;
import com.nook.biz.node.framework.xray.cli.XrayInboundCli;
import com.nook.biz.node.framework.xray.cli.XrayOutboundCli;
import com.nook.biz.node.framework.xray.cli.XrayRoutingCli;
import com.nook.biz.node.framework.xray.cli.XrayStatsCli;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.node.validator.XrayNodeValidator;
import com.nook.biz.operation.api.OpProgressSink;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Xray client 操作执行体: op queue handler 调度的 SSH + DB 真正执行点. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientOpExecutor {

    private final XrayClientMapper xrayClientMapper;
    private final XrayClientTrafficMapper xrayClientTrafficMapper;
    private final XrayInboundCli inboundCli;
    private final XrayOutboundCli outboundCli;
    private final XrayRoutingCli routingCli;
    private final XrayStatsCli statsCli;
    private final XrayNodeService xrayNodeService;
    private final ResourceIpPoolService resourceIpPoolService;
    private final XrayClientValidator clientValidator;
    private final XrayNodeValidator xrayNodeValidator;
    private final ResourceIpPoolValidator ipPoolValidator;
    private final XrayDaemonProbe xrayDaemonProbe;
    private final TransactionTemplate transactionTemplate;

    /**
     * 开通客户端.
     *
     * @param reqVO    开通入参
     * @param progress 进度 sink, 允许为 null
     * @return 落库后的客户端
     */
    @Transactional(rollbackFor = Exception.class)
    XrayClientDO doProvision(XrayClientProvisionReqVO reqVO, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("校验入参", 15);
        clientValidator.validateForProvision(reqVO);
        clientValidator.validateIpNotInUse(reqVO.getIpId());

        sink.report("加载服务器信息", 25);
        XrayNodeDO node = xrayNodeValidator.validateExists(reqVO.getServerId());
        clientValidator.validateTouchdownCapacity(reqVO.getServerId(), node.getTouchdownSize());

        // 同事务回滚时 IP 自动归还
        sink.report("占用落地 IP", 35);
        ResourceIpPoolDO ipEntry = resourceIpPoolService.occupyById(reqVO.getIpId(), reqVO.getMemberUserId());
        if (StrUtil.isBlank(ipEntry.getIpAddress()) || ObjectUtil.isNull(ipEntry.getSocks5Port())) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    reqVO.getIpId(), "落地 IP 的 SOCKS5 凭据未配置");
        }

        sink.report("生成客户端凭据", 45);
        String clientId = UUID.randomUUID().toString().replace("-", "");
        int listenPort = node.getSharedInboundPort();
        String inboundTag = SHARED_INBOUND_TAG;
        String outboundTag = outboundTagOf(clientId);
        String ruleTag = ruleTagOf(clientId);
        String clientUuid = UUID.randomUUID().toString();
        String clientEmail = "member_" + reqVO.getMemberUserId() + "_" + reqVO.getIpId();

        InboundUserSpec userSpec = InboundUserSpec.builder()
                .email(clientEmail)
                .uuid(clientUuid)
                .protocol(node.getProtocol())
                .flow("")
                .totalBytes(reqVO.getTotalBytes() == null ? 0L : reqVO.getTotalBytes())
                .expiryEpochMillis(reqVO.getExpiryEpochMillis() == null ? 0L : reqVO.getExpiryEpochMillis())
                .limitIp(reqVO.getLimitIp() == null ? 0 : reqVO.getLimitIp())
                .build();

        sink.report("保存客户端", 55);
        XrayClientDO entity = XrayClientDO.builder()
                .id(clientId)
                .serverId(reqVO.getServerId())
                .ipId(reqVO.getIpId())
                .memberUserId(reqVO.getMemberUserId())
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
        String xrayBin = node.getXrayBinaryPath();
        sink.report("连接服务器", 65);
        SshSession session = SshSessions.acquire(reqVO.getServerId(), SshSessionScope.SHARED);
        try {
            statsCli.readUserTraffic(session, xrayBin, apiPort, clientEmail, true);
        } catch (Exception ignore) { }

        // 远端写非事务, 用旗标记录已成功的步骤, 出错时按相反顺序手动撤销
        boolean userAdded = false;
        boolean ruleAdded = false;
        boolean socksAdded = false;
        try {
            sink.report("注册客户到 Xray", 72);
            inboundCli.addUser(session, xrayBin, apiPort, inboundTag, userSpec);
            userAdded = true;

            sink.report("下发路由规则", 80);
            routingCli.addRule(session, xrayBin, apiPort, ruleTag,
                    java.util.Collections.singletonList(clientEmail), outboundTag);
            ruleAdded = true;

            sink.report("下发落地出口", 90);
            outboundCli.addSocksOutbound(session, xrayBin, apiPort, outboundTag,
                    ipEntry.getIpAddress(), ipEntry.getSocks5Port(),
                    ipEntry.getSocks5Username(), ipEntry.getSocks5Password());
            socksAdded = true;
        } catch (RuntimeException e) {
            log.error("[provision] CLI 失败 server={} client={} email={} stage=[user={} rule={} socks={}]",
                    reqVO.getServerId(), clientId, clientEmail, userAdded, ruleAdded, socksAdded, e);
            if (socksAdded) {
                try { outboundCli.removeOutbound(session, xrayBin, apiPort, outboundTag); }
                catch (Exception ignore) { }
            }
            if (ruleAdded) {
                try { routingCli.removeRule(session, xrayBin, apiPort, ruleTag); }
                catch (Exception ignore) { }
            }
            if (userAdded) {
                try { inboundCli.removeUser(session, xrayBin, apiPort, inboundTag, clientEmail); }
                catch (Exception ignore) { }
            }
            throw e;
        }
        log.info("[provision] OK server={} client={} port={} email={} ip={}",
                reqVO.getServerId(), clientId, listenPort, clientEmail, ipEntry.getIpAddress());
        return entity;
    }

    private static final String SHARED_INBOUND_TAG = XrayConstants.SHARED_INBOUND_TAG;

    /**
     * 吊销客户端.
     *
     * @param inboundEntityId xray_client.id
     * @param progress        进度 sink, 允许为 null
     */
    void doRevoke(String inboundEntityId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;

        sink.report("加载客户端", 20);
        XrayClientDO e = clientValidator.validateExists(inboundEntityId);
        String outboundTag = outboundTagOf(e.getId());
        String ruleTag = ruleTagOf(e.getId());
        XrayNodeDO node = xrayNodeValidator.validateExists(e.getServerId());
        int apiPort = node.getXrayApiPort();
        String xrayBin = node.getXrayBinaryPath();

        sink.report("清理本地数据", 50);
        transactionTemplate.executeWithoutResult(txStatus -> {
            xrayClientMapper.deleteById(e.getId());
            xrayClientTrafficMapper.deleteByClientId(e.getId());
            // forRevoke 变体跟当前事务共享, 看得到刚 deleteById 的行, 绕开"客户端仍占用"校验
            try {
                resourceIpPoolService.releaseToCoolingForRevoke(e.getIpId());
            } catch (RuntimeException re) {
                log.warn("[revoke] IP 退订失败 ipId={}: {} (DB 主流程已完成)",
                        e.getIpId(), re.getMessage());
            }
        });

        // SSH 副作用不可回滚, 故放事务外; 失败留孤儿不抛错, 由对账兜底
        sink.report("连接服务器", 70);
        try {
            SshSession session = SshSessions.acquire(e.getServerId(), SshSessionScope.SHARED);
            sink.report("清理服务器配置", 85);
            cleanupRemoteAfterRevoke(session, xrayBin, apiPort, SHARED_INBOUND_TAG,
                    e.getClientEmail(), ruleTag, outboundTag, e.getServerId());
        } catch (Exception ex) {
            log.warn("[revoke] DB 已提交, 但 SSH 清理失败 server={} 远端可能留孤儿 user/rule/outbound: {}",
                    e.getServerId(), ex.getMessage());
        }

        log.info("[revoke] OK server={} client={} email={}",
                e.getServerId(), e.getId(), e.getClientEmail());
    }

    /** 远端清理 user / rule / outbound; 单步失败仅 warn, 由对账兜底. */
    private void cleanupRemoteAfterRevoke(SshSession session, String xrayBin, int apiPort,
                                           String inboundTag, String email,
                                           String ruleTag, String outboundTag, String serverId) {
        try {
            inboundCli.removeUser(session, xrayBin, apiPort, inboundTag, email);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
            log.warn("[revoke] user 已不存在 server={} email={}", serverId, email);
        }

        try {
            routingCli.removeRule(session, xrayBin, apiPort, ruleTag);
        } catch (RuntimeException re) {
            log.warn("[revoke] routing rule 删除失败 server={} ruleTag={}: {}",
                    serverId, ruleTag, re.getMessage());
        }

        // 不补占位 outbound, 没匹配 rule 的流量直接走 blackhole 兜底
        try {
            outboundCli.removeOutbound(session, xrayBin, apiPort, outboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
    }

    /**
     * 轮换客户端密钥; CLI 失败则事务回滚 DB, 由 status=3 + 对账兜底.
     *
     * @param inboundEntityId xray_client.id
     * @param progress        进度 sink, 允许为 null
     * @return 更新后的客户端 (clientUuid 已换)
     */
    XrayClientDO doRotate(String inboundEntityId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("加载客户端", 20);
        XrayClientDO e = clientValidator.validateExists(inboundEntityId);
        XrayNodeDO node = xrayNodeValidator.validateExists(e.getServerId());
        String newUuid = UUID.randomUUID().toString();
        String email = e.getClientEmail();

        sink.report("连接服务器", 40);
        SshSession session = SshSessions.acquire(e.getServerId(), SshSessionScope.SHARED);

        sink.report("更新密钥", 70);
        int apiPort = node.getXrayApiPort();
        String xrayBin = node.getXrayBinaryPath();
        try {
            transactionTemplate.executeWithoutResult(txStatus -> {
                xrayClientMapper.updateClientUuid(e.getId(), newUuid);
                try {
                    inboundCli.removeUser(session, xrayBin, apiPort, SHARED_INBOUND_TAG, email);
                } catch (BusinessException be) {
                    if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
                }
                InboundUserSpec spec = InboundUserSpec.builder()
                        .email(email).uuid(newUuid).protocol(node.getProtocol()).flow("").build();
                inboundCli.addUser(session, xrayBin, apiPort, SHARED_INBOUND_TAG, spec);
            });
        } catch (RuntimeException remoteErr) {
            // 事务已回滚 DB 还是老 UUID; 但 rmu 可能已生效, 标 status=3 让对账重推老 UUID 恢复连接
            log.warn("[rotate] CLI 失败 DB 已回滚 server={} client={} email={}: {} (status=3, 待对账)",
                    e.getServerId(), e.getId(), email, remoteErr.getMessage());
            xrayClientMapper.updateStatus(e.getId(), 3, LocalDateTime.now());
            throw remoteErr;
        }

        e.setClientUuid(newUuid);
        log.info("[rotate] OK server={} client={} email={} 新 UUID 已生效",
                e.getServerId(), e.getId(), email);
        return e;
    }

    /**
     * 单客户端推远端 (user + rule + outbound 幂等重建).
     *
     * @param clientId xray_client.id
     * @param progress 进度 sink, 允许为 null
     */
    void doSyncOne(String clientId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        XrayClientDO c = clientValidator.validateExists(clientId);
        sink.report("加载服务器信息", 20);
        XrayNodeDO node = xrayNodeValidator.validateExists(c.getServerId());
        sink.report("连接服务器", 30);
        SshSession session = SshSessions.acquire(c.getServerId(), SshSessionScope.RECONCILE);
        sink.report("加载落地凭据", 40);
        ResourceIpPoolDO ipEntry = ipPoolValidator.validateExists(c.getIpId());
        syncSingle(session, node, c, ipEntry, sink);
    }

    /**
     * 流量清零; 以远端当前累计作为新基线, 后续采样从此起算.
     *
     * @param clientId xray_client.id
     * @param progress 进度 sink, 允许为 null
     */
    @Transactional(rollbackFor = Exception.class)
    void doResetTraffic(String clientId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("校验客户端", 20);
        XrayClientDO client = clientValidator.validateExists(clientId);
        sink.report("加载服务器信息", 40);
        XrayNodeDO node = xrayNodeValidator.validateExists(client.getServerId());
        sink.report("读取服务器流量", 60);
        SshSession session = SshSessions.acquire(client.getServerId(), SshSessionScope.SHARED);
        var snap = statsCli.readUserTraffic(session, node.getXrayBinaryPath(), node.getXrayApiPort(), client.getClientEmail(), false);
        sink.report("保存流量基线", 85);
        xrayClientTrafficMapper.resetWithBaseline(
                UUID.randomUUID().toString().replace("-", ""),
                clientId, client.getServerId(),
                Math.max(0L, snap.getUpBytes()),
                Math.max(0L, snap.getDownBytes()),
                LocalDateTime.now());
        log.info("[reset-traffic] OK server={} client={} email={} cur=up{}/down{}",
                client.getServerId(), clientId, client.getClientEmail(), snap.getUpBytes(), snap.getDownBytes());
    }

    /**
     * 同步全部客户端 (CLIENT_ALL_SYNC): 把该 server 下所有非停 client 三段配置幂等推到远端.
     *
     * @param serverId resource_server.id
     * @param progress 进度 sink, 允许为 null
     * @return 回放报告
     */
    XrayClientReplayReportRespVO doReplayServer(String serverId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("加载服务器信息", 15);
        XrayNodeDO node = xrayNodeValidator.validateExists(serverId);
        sink.report("连接服务器", 25);
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.RECONCILE);
        return replayInternal(session, node, sink);
    }

    /**
     * 重放核心: 拉 DB 目标列表 + 逐客户端幂等推远端.
     *
     * @param session  已建立的 SSH session (caller 持有生命周期)
     * @param node     xray 节点配置
     * @param progress 进度 sink, 允许为 null
     * @return 回放报告
     */
    private XrayClientReplayReportRespVO replayInternal(SshSession session, XrayNodeDO node, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        String serverId = node.getServerId();

        sink.report("加载待同步列表", 35);
        List<XrayClientDO> targets = new ArrayList<>();
        for (XrayClientDO c : xrayClientMapper.selectByServerId(serverId)) {
            if (c.getStatus() != null && c.getStatus() == 2) continue;
            targets.add(c);
        }

        sink.report("加载落地凭据", 50);
        Set<String> ipIds = CollectionUtils.convertSet(targets, XrayClientDO::getIpId);
        Map<String, ResourceIpPoolDO> ipMap = resourceIpPoolService.getIpPoolMap(ipIds);

        int success = 0;
        List<String> failed = new ArrayList<>();
        int total = targets.size();
        int idx = 0;
        for (XrayClientDO c : targets) {
            idx++;
            if (total > 0) {
                int pct = 55 + (40 * idx) / total;
                sink.report("同步客户端 " + idx + "/" + total, Math.min(95, pct));
            }
            try {
                syncSingle(session, node, c, ipMap.get(c.getIpId()), OpProgressSink.noop());
                success++;
            } catch (Exception ex) {
                log.error("[reconciler] 同步失败 客户端={} 邮箱={}: {}",
                        c.getId(), c.getClientEmail(), ex.getMessage());
                failed.add(c.getId());
            }
        }

        XrayClientReplayReportRespVO report = new XrayClientReplayReportRespVO();
        report.setServerId(serverId);
        report.setTotalCount(total);
        report.setAlreadyOkCount(0);
        report.setSuccessCount(success);
        report.setFailedClientIds(failed);
        log.info("[reconciler] 回放完成 服务器={} 总数={} 同步成功={} 失败={}",
                serverId, total, success, failed.size());
        return report;
    }

    /**
     * 把单客户端三段配置 (user / rule / outbound) 先删后加幂等推到远端.
     *
     * @param session  已建立的 SSH session
     * @param node     xray 节点配置
     * @param c        目标客户端 (caller 已 validateExists)
     * @param ipEntry  落地 IP 凭据; 缺失则标 status=3 并抛错
     * @param progress 进度 sink; replay 循环传 noop
     */
    private void syncSingle(SshSession session, XrayNodeDO node, XrayClientDO c,
                            ResourceIpPoolDO ipEntry, OpProgressSink progress) {
        if (ipEntry == null || StrUtil.isBlank(ipEntry.getIpAddress()) || ObjectUtil.isNull(ipEntry.getSocks5Port())) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    c.getIpId(), "落地 IP 凭据丢失, 无法 sync");
        }

        String xrayBin = node.getXrayBinaryPath();
        int apiPort = node.getXrayApiPort();
        String outboundTag = outboundTagOf(c.getId());
        String ruleTag = ruleTagOf(c.getId());

        progress.report("移除旧客户配置", 55);
        try {
            inboundCli.removeUser(session, xrayBin, apiPort, SHARED_INBOUND_TAG, c.getClientEmail());
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        progress.report("下发新客户配置", 65);
        InboundUserSpec spec = InboundUserSpec.builder()
                .email(c.getClientEmail())
                .uuid(c.getClientUuid())
                .protocol(node.getProtocol())
                .flow("")
                .build();
        try {
            inboundCli.addUser(session, xrayBin, apiPort, SHARED_INBOUND_TAG, spec);
        } catch (RuntimeException addErr) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw addErr;
        }

        progress.report("移除旧路由规则", 72);
        try {
            routingCli.removeRule(session, xrayBin, apiPort, ruleTag);
        } catch (RuntimeException ignore) { }
        progress.report("下发新路由规则", 78);
        try {
            routingCli.addRule(session, xrayBin, apiPort, ruleTag,
                    Collections.singletonList(c.getClientEmail()), outboundTag);
        } catch (RuntimeException addErr) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw addErr;
        }

        progress.report("移除旧落地出口", 85);
        try {
            outboundCli.removeOutbound(session, xrayBin, apiPort, outboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        progress.report("下发新落地出口", 92);
        try {
            outboundCli.addSocksOutbound(session, xrayBin, apiPort, outboundTag,
                    ipEntry.getIpAddress(), ipEntry.getSocks5Port(),
                    ipEntry.getSocks5Username(), ipEntry.getSocks5Password());
        } catch (RuntimeException addErr) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw addErr;
        }

        xrayClientMapper.updateStatus(c.getId(), 1, LocalDateTime.now());
        progress.report("更新状态", 95);
    }

}
