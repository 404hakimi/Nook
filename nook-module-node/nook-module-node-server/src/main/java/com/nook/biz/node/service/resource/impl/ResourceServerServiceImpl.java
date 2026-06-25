package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.agent.api.AgentTokenApi;
import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.ResourceServerCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.ResourceServerRuntimeDO;
import com.nook.biz.node.mapper.ResourceServerBillingMapper;
import com.nook.biz.node.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.mapper.Socks5InstallMapper;
import com.nook.biz.node.mapper.ResourceServerQuotaMapper;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.mapper.ResourceServerTrafficMapper;
import com.nook.biz.node.mapper.XrayInboundMapper;
import com.nook.biz.node.mapper.XrayInstallMapper;
import com.nook.biz.node.service.resource.ResourceServerBillingService;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.rules.ResourceServerRules;
import com.nook.biz.node.lifecycle.ServerLifecycleValidator;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
    private ResourceServerQuotaMapper resourceServerQuotaMapper;
    @Resource
    private ResourceServerRuntimeMapper resourceServerRuntimeMapper;
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
    private ResourceServerLandingService resourceServerLandingService;
    @Resource
    private AgentTokenApi agentTokenApi;
    @Resource
    private ResourceServerCredentialMapper resourceServerCredentialMapper;
    @Resource
    private ResourceServerBillingMapper resourceServerBillingMapper;
    @Resource
    private Socks5InstallMapper socks5InstallMapper;
    @Resource
    private ResourceServerTrafficMapper resourceServerTrafficMapper;
    @Resource
    private XrayInstallMapper xrayInstallMapper;
    @Resource
    private XrayInboundMapper xrayInboundMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createServer(ResourceServerCreateReqVO createReqVO) {
        // 校验类型 / 生命周期取值 + 名称唯一; 落地机另查 IP 类型与出网 IP
        resourceServerValidator.validateServerType(createReqVO.getServerType());
        resourceServerValidator.validateLifecycleState(createReqVO.getLifecycleState());
        resourceServerValidator.validateNameUnique(null, createReqVO.getName());
        boolean isLanding = ResourceServerTypeEnum.LANDING.matches(createReqVO.getServerType());
        if (isLanding) {
            resourceServerLandingValidator.validateForCreate(createReqVO.getIpTypeId(), createReqVO.getIpAddress());
        }
        // 签发 agent 鉴权 token
        String agentToken = agentTokenApi.generateToken();
        // 主表 (ipAddress 作为 SSH 主机地址)
        ResourceServerDO entity = new ResourceServerDO();
        entity.setServerType(createReqVO.getServerType());
        entity.setName(createReqVO.getName());
        entity.setIpAddress(createReqVO.getIpAddress());
        entity.setRegion(createReqVO.getRegion());
        entity.setRemark(createReqVO.getRemark());
        entity.setLifecycleState(createReqVO.getLifecycleState());
        entity.setAgentToken(agentToken);
        resourceServerMapper.insert(entity);

        // 共用子表
        resourceServerCredentialService.create(entity.getId(), createReqVO.getCredential());
        this.initQuotaAndRuntime(entity.getId());

        // 类型分支子表
        if (isLanding) {
            resourceServerLandingService.initSubtables(entity.getId(), createReqVO.getIpTypeId());
        } else {
            resourceServerBillingService.create(entity.getId(), null);
        }
        return entity.getId();
    }

    private void initQuotaAndRuntime(String serverId) {
        LocalDateTime now = LocalDateTime.now();

        // 额度配置占位行; 上限 admin 后填, 运行统计首次上报时建测量行
        ResourceServerQuotaDO quota = new ResourceServerQuotaDO();
        quota.setServerId(serverId);
        quota.setResetPolicy(ResourceServerQuotaResetPolicyEnum.MONTHLY.getState());
        quota.setResetDay(ResourceServerRules.DEFAULT_RESET_DAY);
        quota.setUsablePercent(ResourceServerRules.DEFAULT_USABLE_PERCENT);
        quota.setCreatedAt(now);
        quota.setUpdatedAt(now);
        resourceServerQuotaMapper.insert(quota);

        ResourceServerRuntimeDO runtime = new ResourceServerRuntimeDO();
        runtime.setServerId(serverId);
        runtime.setConsecutiveMiss(0);
        runtime.setUpdatedAt(now);
        resourceServerRuntimeMapper.insert(runtime);
    }

    @Override
    public void updateResourceServer(String id, ResourceServerCoreUpdateReqVO reqVO) {
        // 校验存在 + 名称唯一 + 区域可改性 (上线后锁定)
        ResourceServerDO existing = resourceServerValidator.validateExists(id);
        resourceServerValidator.validateNameUnique(id, reqVO.getName());
        resourceServerValidator.validateRegionMutable(existing, reqVO.getRegion());
        // 更新核心字段; null 字段由 MP NOT_NULL 策略跳过
        ResourceServerDO updateObj = BeanUtils.toBean(reqVO, ResourceServerDO.class);
        updateObj.setId(id);
        resourceServerMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServer(String id) {
        ResourceServerDO srv = resourceServerValidator.validateExists(id);
        // 删除分级守卫: 装机中/待上线直接删, 运行中/已退役须未被占用绑定 (防误删在用资源)
        serverLifecycleValidator.validateDeletable(srv);
        // 级联删全部子表 + 主表, 同一事务原子; 防遗留孤儿行破坏装机/计量/对账
        resourceServerCredentialMapper.deleteById(id);
        resourceServerBillingMapper.deleteById(id);
        socks5InstallMapper.deleteById(id);
        resourceServerRuntimeMapper.deleteById(id);
        resourceServerQuotaMapper.deleteById(id);
        int trafficRows = resourceServerTrafficMapper.deleteByServerId(id);
        xrayInstallMapper.deleteById(id);
        xrayInboundMapper.deleteById(id);
        resourceServerMapper.deleteById(id);
        log.info("[deleteServer] 级联删除服务器: id={}, type={}, ip={}, trafficRows={}",
                id, srv.getServerType(), srv.getIpAddress(), trafficRows);
        // 服务器已删, 清掉缓存的 SSH 会话防止下次 acquire 命中失效连接
        SshSessions.invalidate(id);
    }

    @Override
    public ResourceServerDO requireServer(String id) {
        return resourceServerValidator.validateExists(id);
    }

    @Override
    public Map<String, ResourceServerDO> getServerMap(Collection<String> ids) {
        if (CollUtil.isEmpty(ids)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids), ResourceServerDO::getId);
    }

    @Override
    public Map<String, String> getIpAddressMap(Collection<String> ids) {
        if (CollUtil.isEmpty(ids)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids),
                ResourceServerDO::getId,
                ResourceServerDO::getIpAddress);
    }

    @Override
    public Map<String, Long> countByRegion() {
        return resourceServerMapper.countGroupByRegion();
    }
}
