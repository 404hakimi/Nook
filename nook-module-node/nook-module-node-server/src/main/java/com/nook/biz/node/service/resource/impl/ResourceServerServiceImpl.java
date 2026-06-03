package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.agent.api.AgentTokenApi;
import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.ResourceServerCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
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
import com.nook.biz.node.validator.ServerLifecycleValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * 资源服务器 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerServiceImpl implements ResourceServerService {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerCapacityMapper resourceServerCapacityMapper;
    @Resource
    private ResourceServerRuntimeMapper resourceServerRuntimeMapper;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ResourceServerLandingValidator resourceServerLandingValidator;
    @Resource
    private ServerLifecycleValidator serverLifecycleValidator;
    @Resource
    private ResourceServerCredentialService resourceServerCredentialService;
    @Resource
    private ResourceServerBillingService resourceServerBillingService;
    @Resource
    private ResourceServerFrontlineService resourceServerFrontlineService;
    @Resource
    private ResourceServerLandingService resourceServerLandingService;
    @Resource
    private AgentTokenApi agentTokenApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createServer(ResourceServerCreateReqVO createReqVO) {
        resourceServerValidator.validateServerType(createReqVO.getServerType());
        resourceServerValidator.validateLifecycleState(createReqVO.getLifecycleState());
        resourceServerValidator.validateNameUnique(null, createReqVO.getName());

        boolean isLanding = ResourceServerTypeEnum.LANDING.matches(createReqVO.getServerType());
        if (isLanding) {
            resourceServerLandingValidator.validateForCreate(createReqVO.getIpTypeId(), createReqVO.getIpAddress());
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
        resourceServerCredentialService.create(entity.getId(), createReqVO.getCredential());
        initCapacityAndRuntime(entity.getId());

        // 类型分支子表
        if (isLanding) {
            resourceServerLandingService.initSubtables(entity.getId(), createReqVO.getIpTypeId());
        } else {
            resourceServerBillingService.create(entity.getId(), null);
            resourceServerFrontlineService.create(entity.getId(), null);
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
        capacity.setQuotaResetPolicy(ResourceServerQuotaResetPolicyEnum.MONTHLY.getState());
        capacity.setResetDay(1); // 默认每月 1 号重置, admin 可改
        capacity.setThrottleState(ResourceServerThrottleStateEnum.NORMAL.getState());
        capacity.setCreatedAt(now);
        capacity.setUpdatedAt(now);
        resourceServerCapacityMapper.insert(capacity);

        ResourceServerRuntimeDO runtime = new ResourceServerRuntimeDO();
        runtime.setServerId(serverId);
        runtime.setConsecutiveMiss(0);
        runtime.setUpdatedAt(now);
        resourceServerRuntimeMapper.insert(runtime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCore(String id, ResourceServerCoreUpdateReqVO reqVO) {
        resourceServerValidator.validateExists(id);
        resourceServerValidator.validateNameUnique(id, reqVO.getName());

        ResourceServerDO updateObj = BeanUtils.toBean(reqVO, ResourceServerDO.class);
        updateObj.setId(id);
        resourceServerMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServer(String id) {
        resourceServerValidator.validateExists(id);
        resourceServerMapper.deleteById(id);
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    public ResourceServerDO getServer(String id) {
        return resourceServerMapper.selectById(id);
    }

    @Override
    public ResourceServerDO requireServer(String id) {
        return resourceServerValidator.validateExists(id);
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
        if (CollectionUtils.isAnyEmpty(ids)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids), ResourceServerDO::getId);
    }

    @Override
    public Map<String, String> getServerNameMap(Collection<String> ids) {
        if (CollectionUtils.isAnyEmpty(ids)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids),
                ResourceServerDO::getId,
                ResourceServerDO::getName);
    }

    @Override
    public Map<String, String> getIpAddressMap(Collection<String> ids) {
        if (CollectionUtils.isAnyEmpty(ids)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids),
                ResourceServerDO::getId,
                ResourceServerDO::getIpAddress);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transitionLifecycle(String id, String newState) {
        ResourceServerDO srv = resourceServerValidator.validateExists(id);
        if (StrUtil.equals(srv.getLifecycleState(), newState)) {
            return;
        }
        // 转移表 + 各前置守卫 (域名必填 / 占用不可停 / 绑定客户端不可停) 统一收口到生命周期校验器
        serverLifecycleValidator.validateTransition(srv, newState);
        resourceServerMapper.updateLifecycleState(id, newState);
        log.info("[server] LIFECYCLE id={} {} → {}", id, srv.getLifecycleState(), newState);
    }

    @Override
    public Map<String, Long> countByRegion() {
        return resourceServerMapper.countGroupByRegion();
    }
}
