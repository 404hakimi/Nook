package com.nook.biz.node.handler.xray.client;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.service.xray.config.XrayConfigService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.node.validator.XrayServerValidator;
import com.nook.biz.operation.api.OpProgressSink;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

    private final XrayClientMapper xrayClientMapper;
    private final XrayConfigService xrayConfigService;
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
        clientValidator.validateIpNotInUse(reqVO.getIpId());

        sink.report("加载服务器信息", 25);
        XrayDeployment dep = loadDeployment(reqVO.getServerId());

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

        // 只写 DB: 删 client + 释放落地机 同事务原子 (释放失败一起回滚, 避免 client 没了落地机仍占用的泄漏); 远端由 agent reconcile 清掉
        sink.report("清理 DB + 释放落地机", 80);
        transactionTemplate.executeWithoutResult(txStatus -> {
            xrayClientMapper.deleteById(e.getId());
            landingService.releaseToCoolingForRevoke(e.getIpId());
        });

        sink.report("已删, 远端由 agent reconcile 清理", 100);
        log.info("[revoke] DB-only OK server={} client={} email={}; 远端交 reconcile",
                e.getServerId(), e.getId(), e.getClientEmail());
    }

    /**
     * 轮换客户端 UUID (DB-only): 改库即期望态, 远端由 agent reconcile 收敛 (摘旧 UUID 用户 + 装新).
     *
     * @param inboundEntityId xray_client.id
     * @param progress        进度 sink, 允许为 null
     * @return 改库后的客户端 (含新 UUID)
     */
    public XrayClientDO doRotate(String inboundEntityId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("加载客户端", 30);
        XrayClientDO e = clientValidator.validateExists(inboundEntityId);
        String newUuid = UUID.randomUUID().toString();
        sink.report("更新 UUID 入库", 80);
        xrayClientMapper.updateClientUuid(e.getId(), newUuid);
        sink.report("已入库, 远端由 agent reconcile 下发", 100);
        e.setClientUuid(newUuid);
        log.info("[rotate] DB-only OK server={} client={} email={}; 新 UUID 待 reconcile 下发",
                e.getServerId(), e.getId(), e.getClientEmail());
        return e;
    }
}
