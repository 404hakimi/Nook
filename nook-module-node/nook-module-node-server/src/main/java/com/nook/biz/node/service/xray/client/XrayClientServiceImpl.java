package com.nook.biz.node.service.xray.client;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.xray.vo.XrayClientCredentialRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientPageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.handler.xray.client.ClientOpExecutor;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.config.XrayConfigService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.XrayServerValidator;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.dto.OpEnqueueRequest;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.api.spi.OpOrchestrator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import com.nook.framework.security.stp.StpSystemUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Xray 客户端 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XrayClientServiceImpl implements XrayClientService {

    private final XrayClientMapper xrayClientMapper;
    private final XrayConfigService xrayConfigService;
    private final XrayServerValidator xrayServerValidator;
    private final ResourceServerValidator serverValidator;
    private final ResourceServerService resourceServerService;
    private final XrayClientValidator clientValidator;
    private final ClientOpExecutor clientOpExecutor;
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
        // DB-only 开通, 直接走 executor; 远端由 agent reconcile 收敛, 不再经 op 队列
        return clientOpExecutor.doProvision(reqVO, null);
    }

    @Override
    public void revokeXrayClient(String inboundEntityId) {
        // DB-only 吊销, 直接走 executor; 远端由 agent reconcile 清理
        clientOpExecutor.doRevoke(inboundEntityId, null);
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
        // 凭据里的协议 / 传输 / 端口 / listen IP 全部来自 xray_config (server 级共享 inbound)
        xrayServerValidator.validateExists(e.getServerId());
        XrayConfigDO cfg = xrayConfigService.get(e.getServerId());
        XrayClientCredentialRespVO vo = new XrayClientCredentialRespVO();
        vo.setId(e.getId());
        vo.setClientUuid(e.getClientUuid());
        vo.setClientEmail(e.getClientEmail());
        if (cfg == null) return vo;
        vo.setProtocol(cfg.getProtocol());
        // host: 有 domain 优先 (CDN / TLS), 否则回退 server 公网 IP
        if (StrUtil.isNotBlank(cfg.getDomain())) {
            vo.setServerHost(cfg.getDomain());
        } else {
            vo.setServerHost(serverValidator.validateExists(e.getServerId()).getIpAddress());
        }
        vo.setListenPort(cfg.getSharedInboundPort());
        vo.setTransport(cfg.getTransport());
        vo.setWsPath(cfg.getWsPath());
        boolean hasTls = StrUtil.isNotBlank(cfg.getTlsCertPath()) && StrUtil.isNotBlank(cfg.getDomain());
        vo.setTlsEnabled(hasTls);
        vo.setSni(hasTls ? cfg.getDomain() : null);
        return vo;
    }

    @Override
    public Map<String, String> getEmailMap(Collection<String> clientIds) {
        if (CollectionUtils.isAnyEmpty(clientIds)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
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

    @Override
    public EnrichBundle loadEnrichBundle(Set<String> serverIds, Set<String> ipIds) {
        Map<String, ResourceServerDO> serverMap =
                CollectionUtils.isAnyEmpty(serverIds)
                        ? Collections.emptyMap() : resourceServerService.getServerMap(serverIds);
        Map<String, String> hostMap = CollectionUtils.isAnyEmpty(serverIds)
                ? Collections.emptyMap() : resourceServerService.getIpAddressMap(serverIds);
        Map<String, XrayConfigDO> configMap =
                CollectionUtils.isAnyEmpty(serverIds)
                        ? Collections.emptyMap() : xrayConfigService.listByServerIds(serverIds);

        Map<String, String> ipMap;
        if (CollectionUtils.isAnyEmpty(ipIds)) {
            ipMap = Collections.emptyMap();
        } else {
            Map<String, ResourceServerDO> landingSrvMap =
                    resourceServerService.getServerMap(ipIds);
            ipMap = new HashMap<>(landingSrvMap.size());
            landingSrvMap.forEach((k, v) -> {
                if (v.getIpAddress() != null) ipMap.put(k, v.getIpAddress());
            });
        }
        return new EnrichBundle(ipMap, serverMap, hostMap, configMap);
    }
}
