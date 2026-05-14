package com.nook.biz.node.service.xray.client;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.xray.vo.XrayClientCredentialRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientPageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientUpdateReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientReplayReportRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientSyncStatusRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.framework.xray.cli.XrayInboundCli;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.support.SessionCredentialMapper;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.dto.EnqueueRequest;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.api.spi.OperationOrchestrator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import com.nook.framework.security.stp.StpSystemUtil;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Xray Client Service 实现类 — 仅 controller-facing 入口.
 *
 * <p>所有写远端的真正业务执行体在 {@link com.nook.biz.node.handler.xray.client.ClientOpExecutor};
 * 本类只做"入队 + DB 查 + convert", 通过 OperationOrchestrator 把 op 投到队列, handler 拉起 executor.
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayClientServiceImpl implements XrayClientService {

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayInboundCli inboundCli;          // getSyncStatus 需要 lsi 拉远端 tag
    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private ResourceServerService resourceServerService;
    @Resource
    private XrayClientValidator clientValidator;
    @Resource
    private SessionCredentialMapper sessionCredentialMapper;
    @Resource
    private OpConfigResolver opConfigResolver;
    @Resource
    private OperationOrchestrator operationOrchestrator;

    @Override
    public XrayClientDO getXrayClient(String id) {
        return clientValidator.validateExists(id);
    }

    @Override
    public PageResult<XrayClientDO> getXrayClientPage(XrayClientPageReqVO pageReqVO) {
        IPage<XrayClientDO> result = xrayClientMapper.selectPageByQuery(
                Page.of(pageReqVO.getPageNo(), pageReqVO.getPageSize()), pageReqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public XrayClientDO provisionXrayClient(XrayClientProvisionReqVO reqVO) {
        EnqueueRequest req = EnqueueRequest.builder()
                .serverId(reqVO.getServerId())
                .opType(OpType.CLIENT_PROVISION.name())
                .operator(currentOperator())
                .paramsJson(JSON.toJSONString(reqVO))
                .allowDuplicate(true) // 新建资源, 多个并发 provision 应排队 FIFO 跑而非互斥
                .build();
        return operationOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_PROVISION.name()), XrayClientDO.class);
    }

    @Override
    public void revokeXrayClient(String inboundEntityId) {
        XrayClientDO e = getXrayClient(inboundEntityId);
        EnqueueRequest req = EnqueueRequest.builder()
                .serverId(e.getServerId())
                .opType(OpType.CLIENT_REVOKE.name())
                .targetId(inboundEntityId)
                .operator(currentOperator())
                .paramsJson("{\"clientId\":\"" + inboundEntityId + "\"}")
                .build();
        operationOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_REVOKE.name()), Void.class);
    }

    @Override
    public XrayClientDO rotateXrayClient(String inboundEntityId) {
        XrayClientDO e = getXrayClient(inboundEntityId);
        EnqueueRequest req = EnqueueRequest.builder()
                .serverId(e.getServerId())
                .opType(OpType.CLIENT_ROTATE.name())
                .targetId(inboundEntityId)
                .operator(currentOperator())
                .paramsJson("{\"clientId\":\"" + inboundEntityId + "\"}")
                .build();
        return operationOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_ROTATE.name()), XrayClientDO.class);
    }

    @Override
    public void updateXrayClient(String inboundEntityId, XrayClientUpdateReqVO updateReqVO) {
        // 校验 client 存在
        clientValidator.validateExists(inboundEntityId);
        // 更新本地元数据; null 字段由 MP NOT_NULL 策略跳过, 即"保留原值"
        XrayClientDO entity = BeanUtils.toBean(updateReqVO, XrayClientDO.class);
        xrayClientMapper.update(entity, Wrappers.<XrayClientDO>lambdaUpdate()
                .eq(XrayClientDO::getId, inboundEntityId));
    }

    @Override
    public XrayClientCredentialRespVO getXrayClientCredential(String inboundEntityId) {
        XrayClientDO e = getXrayClient(inboundEntityId);
        XrayClientCredentialRespVO vo = new XrayClientCredentialRespVO();
        vo.setId(e.getId());
        vo.setClientUuid(e.getClientUuid());
        vo.setClientEmail(e.getClientEmail());
        vo.setProtocol(e.getProtocol());
        // 客户连接的 host = server 公网 IP (resource_server.host); 拼订阅链接用
        vo.setServerHost(resourceServerService.getServer(e.getServerId()).getHost());
        vo.setListenPort(e.getListenPort());
        vo.setTransport(e.getTransport());
        return vo;
    }

    @Override
    public XrayClientSyncStatusRespVO getSyncStatus(String serverId) {
        XrayClientSyncStatusRespVO vo = new XrayClientSyncStatusRespVO();
        vo.setServerId(serverId);
        vo.setOkTags(Collections.emptyList());
        vo.setStaleDbTags(Collections.emptyList());
        vo.setOrphanRemoteTags(Collections.emptyList());

        SshSession session;
        int apiPort;
        try {
            apiPort = xrayNodeService.getXrayNode(serverId).getXrayApiPort();
            session = sessionCredentialMapper.acquire(serverId, SshSessionScope.RECONCILE);
        } catch (RuntimeException e) {
            vo.setReachable(false);
            log.warn("[reconciler] getSyncStatus 不可达 server={}: {}", serverId, e.getMessage());
            return vo;
        }
        vo.setReachable(true);

        // 远端 inbound list; 过滤掉静态预置 (config.json 里 dokodemo "api" inbound).
        // lsi 失败现在会抛 BACKEND_OPERATION_FAILED (避免误判空集); 在 sync-status 只读路径里
        // 等价于"远端不可探测", 标 reachable=false 返回, 不向上 5xx
        Set<String> remote;
        try {
            remote = new HashSet<>(inboundCli.listInbounds(session, apiPort));
        } catch (RuntimeException e) {
            vo.setReachable(false);
            log.warn("[reconciler] getSyncStatus lsi 失败 server={}: {}", serverId, e.getMessage());
            return vo;
        }
        remote.remove("api");

        // DB 里 server 关联的活动 client (status != 2 已停)
        Set<String> dbTags = new HashSet<>();
        for (XrayClientDO c : xrayClientMapper.selectByServerId(serverId)) {
            if (c.getStatus() != null && c.getStatus() == 2) continue;
            if (StrUtil.isNotBlank(c.getExternalInboundRef())) dbTags.add(c.getExternalInboundRef());
        }

        List<String> ok = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String tag : dbTags) {
            if (remote.contains(tag)) ok.add(tag);
            else stale.add(tag);
        }
        List<String> orphan = new ArrayList<>(remote);
        orphan.removeAll(dbTags);

        vo.setOkTags(ok);
        vo.setStaleDbTags(stale);
        vo.setOrphanRemoteTags(orphan);
        return vo;
    }

    @Override
    public void syncXrayClient(String clientId) {
        XrayClientDO c = clientValidator.validateExists(clientId);
        EnqueueRequest req = EnqueueRequest.builder()
                .serverId(c.getServerId())
                .opType(OpType.CLIENT_SYNC.name())
                .targetId(clientId)
                .operator(currentOperator())
                .paramsJson("{\"clientId\":\"" + clientId + "\"}")
                .build();
        operationOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_SYNC.name()), Void.class);
    }

    @Override
    public XrayClientReplayReportRespVO replayServer(String serverId) {
        EnqueueRequest req = EnqueueRequest.builder()
                .serverId(serverId)
                .opType(OpType.SERVER_REPLAY.name())
                .operator(currentOperator())
                .build();
        return operationOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.SERVER_REPLAY.name()), XrayClientReplayReportRespVO.class);
    }

    @Override
    public void replayIfRestarted(String serverId) {
        // 异步入队: reconciler 不关心结果, 让 worker pool 后台跑; 同步 submitAndWait 会让 @Scheduled
        // 单线程串行扫节点时被慢 server 拖死整轮 (timeout 比 cron 大时根本跑不完一遍)
        EnqueueRequest req = EnqueueRequest.builder()
                .serverId(serverId)
                .opType(OpType.SERVER_RECONCILE.name())
                .operator("SCHEDULER")
                .build();
        operationOrchestrator.enqueue(req);
    }

    /**
     * 取当前后台登录的 admin id 作 operator; 没有登录态 (定时器 / 系统调用) 退回 "SYSTEM".
     * 同步路径需在请求线程内直接读 ThreadLocal, 不能延后到 worker 线程.
     */
    private static String currentOperator() {
        try {
            String id = StpSystemUtil.getLoginIdAsString();
            return StrUtil.blankToDefault(id, "SYSTEM");
        } catch (Exception ignore) {
            // 未登录 / 无 token 上下文 — sa-token 抛 NotLoginException
            return "SYSTEM";
        }
    }

    @Override
    public Map<String, String> getEmailMap(Collection<String> clientIds) {
        if (com.nook.common.utils.collection.CollectionUtils.isAnyEmpty(clientIds)) return Collections.emptyMap();
        return com.nook.common.utils.collection.CollectionUtils.convertMap(
                xrayClientMapper.selectBatchIds(clientIds),
                XrayClientDO::getId,
                c -> c.getClientEmail() != null ? c.getClientEmail() : c.getId());
    }

    @Override
    public Map<String, XrayClientDO> getXrayClientMap(Collection<String> clientIds) {
        if (CollectionUtils.isAnyEmpty(clientIds)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                xrayClientMapper.selectBatchIds(clientIds), XrayClientDO::getId);
    }
}
