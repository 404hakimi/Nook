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
 * 本类只做"入队 + DB 查 + convert", 通过 OpOrchestrator 把 op 投到队列, handler 拉起 executor.
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayClientServiceImpl implements XrayClientService {

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayInboundCli inboundCli;
    @Resource
    private XrayOutboundCli outboundCli;
    @Resource
    private XrayRoutingCli routingCli;
    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private XrayNodeValidator xrayNodeValidator;
    @Resource
    private ResourceServerValidator serverValidator;
    @Resource
    private XrayClientValidator clientValidator;
    @Resource
    private OpConfigResolver opConfigResolver;
    @Resource
    private OpOrchestrator opOrchestrator;

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
                .operator(currentOperator())
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
                .operator(currentOperator())
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
                .operator(currentOperator())
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

    /**
     * outbound 维度对账: 远端 socks outbound 的 tag 就是 clientId, 直接跟 DB 活客户 id 集比对.
     * 静态 outbound (blackhole / api / direct 等) 不是 socks 类型, 自然被 kind 过滤掉.
     */
    private static void diffOutbounds(Set<String> dbClientIds, Map<String, String> remoteOutbounds,
                                      XrayClientSyncStatusRespVO vo) {
        List<String> stale = new ArrayList<>();
        for (String id : dbClientIds) {
            if (!"socks".equals(remoteOutbounds.get(id))) stale.add(id);
        }
        List<String> orphan = new ArrayList<>();
        for (Map.Entry<String, String> ent : remoteOutbounds.entrySet()) {
            if (!"socks".equals(ent.getValue())) continue;
            if (!dbClientIds.contains(ent.getKey())) orphan.add(ent.getKey());
        }
        vo.setStaleDbOutbounds(stale);
        vo.setOrphanRemoteOutbounds(orphan);
    }

    /** routing rule 维度对账: rule tag = "rule_" + clientId, 排除 builtin api rule. */
    private static void diffRules(Set<String> dbClientIds, Set<String> remoteRules,
                                  XrayClientSyncStatusRespVO vo) {
        List<String> stale = new ArrayList<>();
        for (String id : dbClientIds) {
            if (!remoteRules.contains("rule_" + id)) stale.add(id);
        }
        List<String> orphan = new ArrayList<>();
        for (String tag : remoteRules) {
            if (XrayConstants.BUILTIN_API_RULE_TAG.equals(tag)) continue;
            if (!tag.startsWith("rule_")) continue;
            String id = tag.substring("rule_".length());
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
                .operator(currentOperator())
                .paramsJson("{\"clientId\":\"" + clientId + "\"}")
                .build();
        opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_SYNC.name()), Void.class);
    }

    @Override
    public XrayClientReplayReportRespVO replayServer(String serverId) {
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(serverId)
                .opType(OpType.SERVER_REPLAY.name())
                .operator(currentOperator())
                .build();
        return opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.SERVER_REPLAY.name()), XrayClientReplayReportRespVO.class);
    }

    @Override
    public void replayIfRestarted(String serverId) {
        // 异步入队: reconciler 不关心结果, 让 worker pool 后台跑; 同步 submitAndWait 会让 @Scheduled
        // 单线程串行扫节点时被慢 server 拖死整轮 (timeout 比 cron 大时根本跑不完一遍)
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(serverId)
                .opType(OpType.SERVER_RECONCILE.name())
                .operator("SCHEDULER")
                .build();
        opOrchestrator.enqueue(req);
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
