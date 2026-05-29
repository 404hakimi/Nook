package com.nook.biz.node.handler.xray.client;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.cli.XrayInboundCli;
import com.nook.biz.node.framework.xray.cli.XrayStatsCli;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.service.xray.config.XrayConfigService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.node.validator.XrayServerValidator;
import com.nook.biz.operation.api.OpProgressSink;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Xray 客户端操作执行器 (handler 委托用; 不在 OpOrchestrator → Handler → Service 链上重新进入 Service 防循环依赖)
 *
 * @author nook
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientOpExecutor {

    private static final String SHARED_INBOUND_TAG = XrayConstants.SHARED_INBOUND_TAG;

    private final XrayClientMapper xrayClientMapper;
    private final XrayClientTrafficMapper xrayClientTrafficMapper;
    private final XrayInboundCli inboundCli;
    private final XrayStatsCli statsCli;
    private final XrayConfigService xrayConfigService;
    private final ResourceServerCapacityMapper capacityMapper;
    private final ResourceServerLandingService landingService;
    private final ResourceServerLandingMapper landingMapper;
    private final XrayClientValidator clientValidator;
    private final XrayServerValidator xrayServerValidator;
    private final TransactionTemplate transactionTemplate;

    /** xray 部署聚合视图: 实例元数据 + inbound 配置; 单方法多处用时复用一次查询 */
    private record XrayDeployment(XrayServerDO server, XrayConfigDO config) { }

    /** 取部署聚合 (server 必存在, config 可能为 null = 未装机完整) */
    private XrayDeployment loadDeployment(String serverId) {
        XrayServerDO server = xrayServerValidator.validateExists(serverId);
        XrayConfigDO config = xrayConfigService.get(serverId);
        return new XrayDeployment(server, config);
    }

    /**
     * 开通客户端 (provision): 占用 landing + 落库 + 远端 inbound/rule/outbound 三段下发
     *
     * @param reqVO    开通入参
     * @param progress 进度 sink, 允许为 null
     * @return 落库后的客户端
     */
    @Transactional(rollbackFor = Exception.class)
    public XrayClientDO doProvision(XrayClientProvisionReqVO reqVO, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("校验入参", 15);
        clientValidator.validateForProvision(reqVO);
        clientValidator.validateIpNotInUse(reqVO.getIpId());

        sink.report("加载服务器信息", 25);
        XrayDeployment dep = loadDeployment(reqVO.getServerId());
        ResourceServerCapacityDO cap = capacityMapper.selectById(reqVO.getServerId());
        Integer clientMaxCount = cap == null ? null : cap.getClientMaxCount();
        clientValidator.validateClientMaxCount(reqVO.getServerId(), clientMaxCount);

        // 同事务回滚时 landing 自动归还 (CAS 失败则抛 BusinessException)
        sink.report("占用落地节点", 35);
        ResourceServerDO landingSrv = landingService.occupyById(reqVO.getIpId(), reqVO.getMemberUserId());
        ResourceServerLandingDO landingSocks = landingMapper.selectByServerId(reqVO.getIpId());
        if (StrUtil.isBlank(landingSrv.getIpAddress()) || landingSocks == null
                || ObjectUtil.isNull(landingSocks.getSocks5Port())) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    reqVO.getIpId(), "落地节点的 SOCKS5 凭据未配置");
        }

        // 校验服务器已装 xray (config 存在), 才允许下单; 远端三段由 agent reconcile 下发
        if (dep.config() == null) {
            throw new BusinessException(XrayErrorCode.SERVER_STATE_NOT_FOUND, reqVO.getServerId());
        }

        sink.report("生成客户端 + 入库", 85);
        String clientId = UUID.randomUUID().toString().replace("-", "");
        String clientUuid = UUID.randomUUID().toString();
        String clientEmail = "member_" + reqVO.getMemberUserId() + "_" + reqVO.getIpId();
        XrayClientDO entity = XrayClientDO.builder()
                .id(clientId)
                .serverId(reqVO.getServerId())
                .ipId(reqVO.getIpId())
                .memberUserId(reqVO.getMemberUserId())
                .clientUuid(clientUuid)
                .clientEmail(clientEmail)
                .totalBytes(reqVO.getTotalBytes() == null ? 0L : reqVO.getTotalBytes())
                .expiryEpochMillis(reqVO.getExpiryEpochMillis() == null ? 0L : reqVO.getExpiryEpochMillis())
                .limitIp(reqVO.getLimitIp() == null ? 0 : reqVO.getLimitIp())
                .status(1)
                .build();
        try {
            xrayClientMapper.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException dke) {
            throw new BusinessException(XrayErrorCode.CLIENT_IP_ALREADY_USED, reqVO.getIpId());
        }

        // 远端不在此同步下发: agent reconcile 拉 DB 期望态后本地 adu/ado/adrules 收敛
        sink.report("已入库, 远端由 agent reconcile 下发", 100);
        log.info("[provision] DB-only OK server={} client={} email={} ip={}; 远端交 reconcile",
                reqVO.getServerId(), clientId, clientEmail, landingSrv.getIpAddress());
        return entity;
    }

    /**
     * 吊销客户端
     *
     * @param inboundEntityId xray_client.id
     * @param progress        进度 sink, 允许为 null
     */
    public void doRevoke(String inboundEntityId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;

        sink.report("加载客户端", 30);
        XrayClientDO e = clientValidator.validateExists(inboundEntityId);

        // 只写 DB: 删 client + 流量 + 释放落地机; 远端 user/rule/outbound 由 agent reconcile 清掉
        sink.report("清理 DB + 释放落地机", 80);
        transactionTemplate.executeWithoutResult(txStatus -> {
            xrayClientMapper.deleteById(e.getId());
            xrayClientTrafficMapper.deleteByClientId(e.getId());
            try {
                landingService.releaseToCoolingForRevoke(e.getIpId());
            } catch (RuntimeException re) {
                log.warn("[revoke] landing 退订失败 serverId={}: {} (DB 主流程已完成)",
                        e.getIpId(), re.getMessage());
            }
        });

        sink.report("已删, 远端由 agent reconcile 清理", 100);
        log.info("[revoke] DB-only OK server={} client={} email={}; 远端交 reconcile",
                e.getServerId(), e.getId(), e.getClientEmail());
    }

    /**
     * 轮换客户端密钥; CLI 失败则事务回滚 DB, 由 status=3 + 对账兜底
     */
    XrayClientDO doRotate(String inboundEntityId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("加载客户端", 20);
        XrayClientDO e = clientValidator.validateExists(inboundEntityId);
        XrayDeployment dep = loadDeployment(e.getServerId());
        String newUuid = UUID.randomUUID().toString();
        String email = e.getClientEmail();

        sink.report("连接服务器", 40);
        SshSession session = SshSessions.acquire(e.getServerId(), SshSessionScope.SHARED);

        sink.report("更新密钥", 70);
        int apiPort = dep.server().getXrayApiPort();
        String xrayBin = dep.server().getXrayBinaryPath();
        String protocol = dep.config() == null ? null : dep.config().getProtocol();
        try {
            transactionTemplate.executeWithoutResult(txStatus -> {
                xrayClientMapper.updateClientUuid(e.getId(), newUuid);
                try {
                    inboundCli.removeUser(session, xrayBin, apiPort, SHARED_INBOUND_TAG, email);
                } catch (BusinessException be) {
                    if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
                }
                InboundUserSpec spec = InboundUserSpec.builder()
                        .email(email).uuid(newUuid).protocol(protocol).flow("").build();
                inboundCli.addUser(session, xrayBin, apiPort, SHARED_INBOUND_TAG, spec);
            });
        } catch (RuntimeException remoteErr) {
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

    /** 流量清零; 以远端当前累计作为新基线, 后续采样从此起算 */
    @Transactional(rollbackFor = Exception.class)
    void doResetTraffic(String clientId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("校验客户端", 20);
        XrayClientDO client = clientValidator.validateExists(clientId);
        sink.report("加载服务器信息", 40);
        XrayServerDO server = xrayServerValidator.validateExists(client.getServerId());
        sink.report("读取服务器流量", 60);
        SshSession session = SshSessions.acquire(client.getServerId(), SshSessionScope.SHARED);
        var snap = statsCli.readUserTraffic(session, server.getXrayBinaryPath(), server.getXrayApiPort(),
                client.getClientEmail(), false);
        sink.report("保存流量基线", 85);
        xrayClientTrafficMapper.resetWithBaseline(
                UUID.randomUUID().toString().replace("-", ""),
                clientId, client.getServerId(),
                Math.max(0L, snap.getUpBytes()),
                Math.max(0L, snap.getDownBytes()),
                LocalDateTime.now());
        log.info("[reset-traffic] OK server={} client={} email={} cur=up{}/down{}",
                client.getServerId(), clientId, client.getClientEmail(),
                snap.getUpBytes(), snap.getDownBytes());
    }

}
