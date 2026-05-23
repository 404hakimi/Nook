package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.controller.resource.vo.ResourceServerCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDnsDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerDnsMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.event.ServerCredentialChangedEvent;
import com.nook.biz.node.service.resource.ResourceServerBillingService;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerDnsService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 资源服务器 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServerServiceImpl implements ResourceServerService {

    private final ResourceServerMapper resourceServerMapper;
    private final ResourceServerCapacityMapper resourceServerCapacityMapper;
    private final ResourceServerRuntimeMapper resourceServerRuntimeMapper;
    private final ResourceServerCredentialMapper credentialMapper;
    private final ResourceServerDnsMapper dnsMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ResourceServerValidator serverValidator;
    private final ResourceServerCredentialService credentialService;
    private final ResourceServerBillingService billingService;
    private final ResourceServerDnsService dnsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createServer(ResourceServerCreateReqVO createReqVO) {
        serverValidator.validateNameUnique(null, createReqVO.getName());

        // 1) 写主表 (生成 id + agent_token)
        ResourceServerDO entity = new ResourceServerDO();
        entity.setName(createReqVO.getName());
        entity.setRegion(createReqVO.getRegion());
        entity.setTotalIpCount(createReqVO.getTotalIpCount());
        entity.setRemark(createReqVO.getRemark());
        entity.setLifecycleState(StrUtil.blankToDefault(createReqVO.getLifecycleState(),
                ResourceServerLifecycleEnum.INSTALLING.name()));
        entity.setAgentToken(generateAgentToken());
        resourceServerMapper.insert(entity);

        // 2) credential / billing / dns 子表 + capacity / runtime 占位
        credentialService.create(entity.getId(), createReqVO.getCredential());
        billingService.create(entity.getId(), createReqVO.getBilling());
        dnsService.create(entity.getId(), createReqVO.getDns());
        initCapacityAndRuntime(entity.getId());
        return entity.getId();
    }

    /** SHA256(UUID + UUID) → 64 char hex; 跟 DB CHAR(64) 长度对齐. */
    private static String generateAgentToken() {
        String raw = UUID.randomUUID() + UUID.randomUUID().toString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    private void initCapacityAndRuntime(String serverId) {
        LocalDateTime now = LocalDateTime.now();

        ResourceServerCapacityDO capacity = new ResourceServerCapacityDO();
        capacity.setServerId(serverId);
        capacity.setRxBytes(0L);
        capacity.setTxBytes(0L);
        capacity.setUsedTrafficBytes(0L);
        capacity.setQuotaResetPolicy("CALENDAR_MONTH");
        capacity.setThrottleState("NORMAL");
        capacity.setCreatedAt(now);
        capacity.setUpdatedAt(now);
        resourceServerCapacityMapper.insert(capacity);

        ResourceServerRuntimeDO runtime = new ResourceServerRuntimeDO();
        runtime.setServerId(serverId);
        runtime.setTempUnhealthy(0);
        runtime.setConsecutiveMiss(0);
        runtime.setUpdatedAt(now);
        resourceServerRuntimeMapper.insert(runtime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCore(String id, ResourceServerCoreUpdateReqVO reqVO) {
        serverValidator.validateExists(id);
        serverValidator.validateNameUnique(id, reqVO.getName());

        ResourceServerDO updateObj = BeanUtils.toBean(reqVO, ResourceServerDO.class);
        updateObj.setId(id);
        resourceServerMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServer(String id) {
        serverValidator.validateExists(id);
        resourceServerMapper.deleteById(id);
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    public ResourceServerDO getServer(String id) {
        return resourceServerMapper.selectById(id);
    }

    @Override
    public PageResult<ResourceServerDO> getServerPage(ResourceServerPageReqVO pageReqVO) {
        // host 在 credential 子表; 先查匹配再回主表
        Set<String> hostMatchIds = null;
        if (StrUtil.isNotBlank(pageReqVO.getHost())) {
            hostMatchIds = CollectionUtils.convertSet(
                    credentialMapper.selectByHostLike(pageReqVO.getHost()),
                    ResourceServerCredentialDO::getServerId);
            if (hostMatchIds.isEmpty()) {
                return PageResult.empty();
            }
        }
        IPage<ResourceServerDO> result = resourceServerMapper.selectPageByQuery(
                Page.of(pageReqVO.getPageNo(), pageReqVO.getPageSize()),
                pageReqVO.getName(), pageReqVO.getLifecycleState(), pageReqVO.getRegion(),
                hostMatchIds);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public Map<String, ResourceServerDO> getServerMap(Collection<String> ids) {
        if (CollectionUtils.isAnyEmpty(ids)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids), ResourceServerDO::getId);
    }

    @Override
    public Map<String, String> getServerNameMap(Collection<String> ids) {
        if (CollectionUtils.isAnyEmpty(ids)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids),
                ResourceServerDO::getId,
                ResourceServerDO::getName);
    }

    // 允许的双向流转表; 命名按 from→to, 没列出的组合都拒
    private static final Set<String> ALLOWED_LIFECYCLE_TRANSITIONS = Set.of(
            "INSTALLING→READY", "READY→INSTALLING",
            "READY→LIVE", "LIVE→READY",
            "LIVE→RETIRED", "READY→RETIRED",
            "RETIRED→LIVE"
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transitionLifecycle(String id, String newState) {
        ResourceServerDO srv = serverValidator.validateExists(id);
        if (ResourceServerLifecycleEnum.fromState(newState) == null) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIFECYCLE_INVALID_TRANSITION,
                    srv.getLifecycleState(), newState);
        }
        if (StrUtil.equals(srv.getLifecycleState(), newState)) {
            return;
        }
        String key = srv.getLifecycleState() + "→" + newState;
        if (!ALLOWED_LIFECYCLE_TRANSITIONS.contains(key)) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIFECYCLE_INVALID_TRANSITION,
                    srv.getLifecycleState(), newState);
        }
        // LIVE 前置: dns.domain 必填
        if (ResourceServerLifecycleEnum.LIVE.matches(newState)) {
            ResourceServerDnsDO dns = dnsMapper.selectById(id);
            if (dns == null || StrUtil.isBlank(dns.getDomain())) {
                throw new BusinessException(ResourceErrorCode.SERVER_LIVE_DOMAIN_REQUIRED);
            }
        }
        resourceServerMapper.updateLifecycleState(id, newState);
        log.info("[server] LIFECYCLE id={} {} → {}", id, srv.getLifecycleState(), newState);
    }
}
