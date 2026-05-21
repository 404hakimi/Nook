package com.nook.biz.node.service.xray.client;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.xray.vo.XrayClientCredentialRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientPageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientReplayReportRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientSyncStatusRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.cli.XrayInboundCli;
import com.nook.biz.node.framework.xray.cli.XrayOutboundCli;
import com.nook.biz.node.framework.xray.cli.XrayRoutingCli;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.XrayNodeValidator;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.dto.OpEnqueueRequest;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.api.spi.OpOrchestrator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import com.nook.framework.security.stp.StpSystemUtil;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Xray client controller-facing 入口; 写远端的业务执行体在 ClientOpExecutor, 本类只入队 + 查 + convert. */
@Slf4j
@Service
@RequiredArgsConstructor
public class XrayClientServiceImpl implements XrayClientService {

    private final XrayClientMapper xrayClientMapper;
    private final XrayInboundCli inboundCli;
    private final XrayOutboundCli outboundCli;
    private final XrayRoutingCli routingCli;
    private final XrayNodeService xrayNodeService;
    private final XrayNodeValidator xrayNodeValidator;
    private final ResourceServerValidator serverValidator;
    private final XrayClientValidator clientValidator;
    private final OpConfigResolver opConfigResolver;
    private final OpOrchestrator opOrchestrator;

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
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(reqVO.getServerId())
                .opType(OpType.CLIENT_PROVISION.name())
                .operator(StpSystemUtil.getLoginIdOrSystem())
                .paramsJson(JSON.toJSONString(reqVO))
                .allowDuplicate(true) // 新建资源, 多个并发 provision 应排队 FIFO 跑而非互斥
                .build();
        return opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_PROVISION.name()), XrayClientDO.class);
    }

    @Override
    public void revokeXrayClient(String inboundEntityId) {
        XrayClientDO e = getXrayClient(inboundEntityId);
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(e.getServerId())
                .opType(OpType.CLIENT_REVOKE.name())
                .targetId(inboundEntityId)
                .operator(StpSystemUtil.getLoginIdOrSystem())
                .paramsJson("{\"clientId\":\"" + inboundEntityId + "\"}")
                .build();
        opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_REVOKE.name()), Void.class);
    }

    @Override
    public XrayClientDO rotateXrayClient(String inboundEntityId) {
        XrayClientDO e = getXrayClient(inboundEntityId);
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(e.getServerId())
                .opType(OpType.CLIENT_ROTATE.name())
                .targetId(inboundEntityId)
                .operator(StpSystemUtil.getLoginIdOrSystem())
                .paramsJson("{\"clientId\":\"" + inboundEntityId + "\"}")
                .build();
        return opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_ROTATE.name()), XrayClientDO.class);
    }

    @Override
    public XrayClientCredentialRespVO getXrayClientCredential(String inboundEntityId) {
        XrayClientDO e = getXrayClient(inboundEntityId);
        // 凭据里的协议 / 传输 / 端口 / listen IP 全部来自 xray_node, 因为这是 server 级共享 inbound 的属性
        XrayNodeDO node = xrayNodeValidator.validateExists(e.getServerId());
        XrayClientCredentialRespVO vo = new XrayClientCredentialRespVO();
        vo.setId(e.getId());
        vo.setClientUuid(e.getClientUuid());
        vo.setClientEmail(e.getClientEmail());
        vo.setProtocol(node.getProtocol());
        // host: 有 domain 优先 (CDN / TLS), 否则回退 server 公网 IP
        if (StrUtil.isNotBlank(node.getDomain())) {
            vo.setServerHost(node.getDomain());
        } else {
            vo.setServerHost(serverValidator.validateExists(e.getServerId()).getHost());
        }
        vo.setListenPort(node.getSharedInboundPort());
        vo.setTransport(node.getTransport());
        vo.setWsPath(node.getWsPath());
        boolean hasTls = StrUtil.isNotBlank(node.getTlsCertPath()) && StrUtil.isNotBlank(node.getDomain());
        vo.setTlsEnabled(hasTls);
        vo.setSni(hasTls ? node.getDomain() : null);
        return vo;
    }

    @Override
    public XrayClientSyncStatusRespVO getSyncStatus(String serverId) {
        XrayClientSyncStatusRespVO vo = newEmptySyncStatus(serverId);

        // server 未装过 xray (无 xray_node) → reachable=false 静默返回
        XrayNodeDO node = xrayNodeService.getXrayNode(serverId);
        if (node == null) {
            log.debug("[reconciler] 同步状态查询跳过 服务器={} (无 xray 节点)", serverId);
            return vo;
        }
        int apiPort = node.getXrayApiPort();
        String xrayBin = node.getXrayBinaryPath();

        // SSH 不通 → reachable=false 静默返回
        SshSession session;
        try {
            session = SshSessions.acquire(serverId, SshSessionScope.RECONCILE);
        } catch (RuntimeException e) {
            log.warn("[reconciler] 同步状态查询服务器不可达 服务器={}: {}", serverId, e.getMessage());
            return vo;
        }

        // 三维度远端拉取; 任一步失败都视为"远端不可探测", 不向上 5xx
        Set<String> remoteEmails;
        Map<String, String> remoteOutbounds;
        Set<String> remoteRules;
        try {
            remoteEmails = inboundCli.listUsers(session, xrayBin, apiPort, XrayConstants.SHARED_INBOUND_TAG);
            remoteOutbounds = outboundCli.listOutbounds(session, xrayBin, apiPort);
            remoteRules = routingCli.listRuleTags(session, xrayBin, apiPort);
        } catch (RuntimeException e) {
            log.warn("[reconciler] 同步状态查询拉远端列表失败 服务器={}: {}", serverId, e.getMessage());
            return vo;
        }
        vo.setReachable(true);

        // DB 活动 client (status != 2 已停); 维度按 email / clientId 收集
        Set<String> dbEmails = new HashSet<>();
        Set<String> dbClientIds = new HashSet<>();
        for (XrayClientDO c : xrayClientMapper.selectByServerId(serverId)) {
            if (c.getStatus() != null && c.getStatus() == 2) continue;
            if (StrUtil.isNotBlank(c.getClientEmail())) dbEmails.add(c.getClientEmail());
            if (StrUtil.isNotBlank(c.getId())) dbClientIds.add(c.getId());
        }

        diffUsers(dbEmails, remoteEmails, vo);
        diffOutbounds(dbClientIds, remoteOutbounds, vo);
        diffRules(dbClientIds, remoteRules, vo);
        return vo;
    }

    /** sync-status 默认实例: reachable=false + 各维度空集; 失败路径快速返回用. */
    private static XrayClientSyncStatusRespVO newEmptySyncStatus(String serverId) {
        XrayClientSyncStatusRespVO vo = new XrayClientSyncStatusRespVO();
        vo.setServerId(serverId);
        vo.setReachable(false);
        vo.setOkEmails(Collections.emptyList());
        vo.setStaleDbEmails(Collections.emptyList());
        vo.setOrphanRemoteEmails(Collections.emptyList());
        vo.setStaleDbOutbounds(Collections.<String>emptyList());
        vo.setOrphanRemoteOutbounds(Collections.<String>emptyList());
        vo.setStaleDbRules(Collections.<String>emptyList());
        vo.setOrphanRemoteRules(Collections.<String>emptyList());
        return vo;
    }

    /** user 维度对账: 共享 inbound 上 email 与 DB clientEmail 比对. */
    private static void diffUsers(Set<String> dbEmails, Set<String> remoteEmails,
                                  XrayClientSyncStatusRespVO vo) {
        List<String> ok = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String email : dbEmails) {
            if (remoteEmails.contains(email)) ok.add(email);
            else stale.add(email);
        }
        List<String> orphan = new ArrayList<>(remoteEmails);
        orphan.removeAll(dbEmails);
        vo.setOkEmails(ok);
        vo.setStaleDbEmails(stale);
        vo.setOrphanRemoteEmails(orphan);
    }

    /** outbound 维度对账: 业务 socks outbound tag = "out_" + clientId, 静态出站被 socks 类型过滤排除. */
    private static void diffOutbounds(Set<String> dbClientIds, Map<String, String> remoteOutbounds,
                                      XrayClientSyncStatusRespVO vo) {
        List<String> stale = new ArrayList<>();
        for (String id : dbClientIds) {
            if (!"socks".equals(remoteOutbounds.get(XrayConstants.outboundTagOf(id)))) stale.add(id);
        }
        List<String> orphan = new ArrayList<>();
        for (Map.Entry<String, String> ent : remoteOutbounds.entrySet()) {
            if (!"socks".equals(ent.getValue())) continue;
            String tag = ent.getKey();
            if (!tag.startsWith(XrayConstants.OUTBOUND_TAG_PREFIX)) continue;
            String id = tag.substring(XrayConstants.OUTBOUND_TAG_PREFIX.length());
            if (!dbClientIds.contains(id)) orphan.add(id);
        }
        vo.setStaleDbOutbounds(stale);
        vo.setOrphanRemoteOutbounds(orphan);
    }

    /** routing rule 维度对账: rule tag = "rule_" + clientId, 排除 builtin api rule. */
    private static void diffRules(Set<String> dbClientIds, Set<String> remoteRules,
                                  XrayClientSyncStatusRespVO vo) {
        List<String> stale = new ArrayList<>();
        for (String id : dbClientIds) {
            if (!remoteRules.contains(XrayConstants.ruleTagOf(id))) stale.add(id);
        }
        List<String> orphan = new ArrayList<>();
        for (String tag : remoteRules) {
            if (XrayConstants.BUILTIN_API_RULE_TAG.equals(tag)) continue;
            if (!tag.startsWith(XrayConstants.RULE_TAG_PREFIX)) continue;
            String id = tag.substring(XrayConstants.RULE_TAG_PREFIX.length());
            if (!dbClientIds.contains(id)) orphan.add(id);
        }
        vo.setStaleDbRules(stale);
        vo.setOrphanRemoteRules(orphan);
    }

    @Override
    public void syncXrayClient(String clientId) {
        XrayClientDO c = clientValidator.validateExists(clientId);
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(c.getServerId())
                .opType(OpType.CLIENT_SYNC.name())
                .targetId(clientId)
                .operator(StpSystemUtil.getLoginIdOrSystem())
                .paramsJson("{\"clientId\":\"" + clientId + "\"}")
                .build();
        opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_SYNC.name()), Void.class);
    }

    @Override
    public XrayClientReplayReportRespVO replayServer(String serverId) {
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(serverId)
                .opType(OpType.CLIENT_ALL_SYNC.name())
                .operator(StpSystemUtil.getLoginIdOrSystem())
                .build();
        return opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_ALL_SYNC.name()), XrayClientReplayReportRespVO.class);
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
