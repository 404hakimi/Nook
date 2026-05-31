package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.agent.api.AgentTokenApi;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.ResourceServerCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerFrontlineMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.event.ServerCredentialChangedEvent;
import com.nook.biz.node.service.resource.ResourceServerBillingService;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
    private final ResourceServerFrontlineMapper frontlineMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ResourceServerValidator serverValidator;
    private final ResourceServerLandingValidator landingValidator;
    private final ResourceServerCredentialService credentialService;
    private final ResourceServerBillingService billingService;
    private final ResourceServerFrontlineService frontlineService;
    private final ResourceServerLandingService landingService;
    private final AgentTokenApi agentTokenApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createServer(ResourceServerCreateReqVO createReqVO) {
        serverValidator.validateServerType(createReqVO.getServerType());
        serverValidator.validateLifecycleState(createReqVO.getLifecycleState());
        serverValidator.validateNameUnique(null, createReqVO.getName());

        boolean isLanding = ResourceServerTypeEnum.LANDING.matches(createReqVO.getServerType());
        if (isLanding) {
            landingValidator.validateForCreate(createReqVO.getIpTypeId(), createReqVO.getIpAddress());
        }

        // 主表 (id + agentToken; ipAddress 是 canonical SSH 主机)
        ResourceServerDO entity = new ResourceServerDO();
        entity.setServerType(createReqVO.getServerType());
        entity.setName(createReqVO.getName());
        entity.setIpAddress(createReqVO.getIpAddress());
        entity.setRegion(createReqVO.getRegion());
        entity.setRemark(createReqVO.getRemark());
        entity.setLifecycleState(createReqVO.getLifecycleState());
        entity.setAgentToken(agentTokenApi.generateToken());
        resourceServerMapper.insert(entity);

        // 共用子表
        credentialService.create(entity.getId(), createReqVO.getCredential());
        initCapacityAndRuntime(entity.getId());

        // 类型分支子表
        if (isLanding) {
            landingService.initSubtables(entity.getId(), createReqVO.getIpTypeId());
        } else {
            billingService.create(entity.getId(), null);
            frontlineService.create(entity.getId(), null);
        }
        return entity.getId();
    }

    private void initCapacityAndRuntime(String serverId) {
        LocalDateTime now = LocalDateTime.now();

        ResourceServerCapacityDO capacity = new ResourceServerCapacityDO();
        capacity.setServerId(serverId);
        capacity.setRxBytes(0L);
        capacity.setTxBytes(0L);
        capacity.setUsedTrafficBytes(0L);
        capacity.setQuotaResetPolicy(ResourceServerQuotaResetPolicyEnum.FIXED.getState());
        capacity.setThrottleState(ResourceServerThrottleStateEnum.NORMAL.getState());
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
    public ResourceServerDO requireServer(String id) {
        return serverValidator.validateExists(id);
    }

    @Override
    public PageResult<ResourceServerDO> getServerPage(ResourceServerPageReqVO pageReqVO) {
        // ip_address 直接落主表; host 关键字 LIKE ip_address, 不再 JOIN credential 子表
        IPage<ResourceServerDO> result = resourceServerMapper.selectPageByQuery(
                Page.of(pageReqVO.getPageNo(), pageReqVO.getPageSize()),
                pageReqVO.getName(), pageReqVO.getLifecycleState(), pageReqVO.getRegionCodes(),
                pageReqVO.getHost(), null, ResourceServerTypeEnum.FRONTLINE.getState());
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

    @Override
    public Map<String, String> getIpAddressMap(Collection<String> ids) {
        if (CollectionUtils.isAnyEmpty(ids)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids),
                ResourceServerDO::getId,
                ResourceServerDO::getIpAddress);
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
        // LIVE 前置: 仅线路机(frontline)需 domain; 落地机(SOCKS5)无 domain 概念, 跳过
        if (ResourceServerLifecycleEnum.LIVE.matches(newState)
                && ResourceServerTypeEnum.FRONTLINE.matches(srv.getServerType())) {
            ResourceServerFrontlineDO frontline = frontlineMapper.selectById(id);
            if (frontline == null || StrUtil.isBlank(frontline.getDomain())) {
                throw new BusinessException(ResourceErrorCode.SERVER_LIVE_DOMAIN_REQUIRED);
            }
        }
        // 停用(退役)前置: 占用中(OCCUPIED/RESERVED)的落地机不可停用, 否则会留下 RETIRED+占用矛盾态且打扰在用会员
        if (ResourceServerLifecycleEnum.RETIRED.matches(newState)
                && ResourceServerTypeEnum.LANDING.matches(srv.getServerType())) {
            String landingStatus = landingService.getLanding(id).getStatus();
            if (ResourceServerLandingStatusEnum.OCCUPIED.matches(landingStatus)
                    || ResourceServerLandingStatusEnum.RESERVED.matches(landingStatus)) {
                throw new BusinessException(ResourceErrorCode.LANDING_IN_USE_CANNOT_RETIRE);
            }
        }
        resourceServerMapper.updateLifecycleState(id, newState);
        log.info("[server] LIFECYCLE id={} {} → {}", id, srv.getLifecycleState(), newState);
    }

    @Override
    public Map<String, Long> countByRegion() {
        return resourceServerMapper.countGroupByRegion();
    }
}
