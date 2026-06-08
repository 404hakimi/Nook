package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerProvisionModeEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingQuotaUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerQuotaDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerBillingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerQuotaMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SOCKS5 落地节点 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerLandingServiceImpl implements ResourceServerLandingService {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerLandingMapper resourceServerLandingMapper;
    @Resource
    private ResourceServerBillingMapper resourceServerBillingMapper;
    @Resource
    private ResourceServerQuotaMapper resourceServerQuotaMapper;
    @Resource
    private ResourceServerRuntimeMapper resourceServerRuntimeMapper;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ResourceServerLandingValidator resourceServerLandingValidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initSubtables(String serverId, String ipTypeId) {
        // 只填业务必填字段; SOCKS5 凭据 / dante install 字段留 null, 由"部署"流程前端 prefill 后写入
        LocalDateTime now = LocalDateTime.now();
        ResourceServerLandingDO lan = new ResourceServerLandingDO();
        lan.setServerId(serverId);
        lan.setIpTypeId(ipTypeId);
        lan.setProvisionMode(ResourceServerProvisionModeEnum.SELF_DEPLOY.getCode());
        lan.setStatus(ResourceServerLandingStatusEnum.AVAILABLE.getState());
        lan.setAssignCount(0);
        lan.setCreatedAt(now);
        lan.setUpdatedAt(now);
        resourceServerLandingMapper.insert(lan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCore(String id, ServerLandingCoreUpdateReqVO reqVO) {
        resourceServerValidator.validateExists(id);
        resourceServerLandingValidator.validateExists(id);
        resourceServerLandingValidator.validateIpTypeExists(reqVO.getIpTypeId());
        resourceServerLandingValidator.validateIpAddressUnique(id, reqVO.getIpAddress());

        ResourceServerDO srvPatch = new ResourceServerDO();
        srvPatch.setId(id);
        srvPatch.setRegion(reqVO.getRegion());
        srvPatch.setIpAddress(reqVO.getIpAddress());
        srvPatch.setRemark(reqVO.getRemark());
        resourceServerMapper.updateById(srvPatch);

        ResourceServerLandingDO lanPatch = new ResourceServerLandingDO();
        lanPatch.setServerId(id);
        lanPatch.setIpTypeId(reqVO.getIpTypeId());
        lanPatch.setProvisionMode(reqVO.getProvisionMode());
        resourceServerLandingMapper.updateBySelective(lanPatch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSocks5(String id, ServerLandingSocks5UpdateReqVO reqVO) {
        resourceServerValidator.validateExists(id);
        resourceServerLandingValidator.validateExists(id);

        ResourceServerLandingDO patch = BeanUtils.toBean(reqVO, ResourceServerLandingDO.class);
        patch.setServerId(id);
        // 密码留空 = 保留原值
        if (StrUtil.isBlank(patch.getSocks5Password())) {
            patch.setSocks5Password(null);
        }
        resourceServerLandingMapper.updateBySelective(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBilling(String id, ServerLandingBillingUpdateReqVO reqVO) {
        resourceServerValidator.validateExists(id);
        LocalDateTime now = LocalDateTime.now();

        ResourceServerBillingDO old = resourceServerBillingMapper.selectById(id);
        if (ObjectUtil.isNull(old)) {
            ResourceServerBillingDO bill = BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
            bill.setServerId(id);
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);
            resourceServerBillingMapper.insert(bill);
            return;
        }
        ResourceServerBillingDO patch = BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
        patch.setServerId(id);
        resourceServerBillingMapper.updateBySelective(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuota(String id, ServerLandingQuotaUpdateReqVO reqVO) {
        resourceServerValidator.validateExists(id);
        // 配额配置行装机时已 insert 占位, 这里只走 update 上限
        resourceServerQuotaMapper.updateQuota(id, reqVO.getTotalGb(), reqVO.getBandwidthMbps(),
                reqVO.getResetPolicy(), reqVO.getResetDay());
    }

    @Override
    public ResourceServerDO getServer(String id) {
        return resourceServerValidator.validateExists(id);
    }

    @Override
    public ResourceServerLandingDO getLanding(String id) {
        return resourceServerLandingValidator.validateExists(id);
    }

    @Override
    public ResourceServerBillingDO getBilling(String id) {
        return resourceServerBillingMapper.selectById(id);
    }

    @Override
    public ResourceServerQuotaDO getQuota(String id) {
        return resourceServerQuotaMapper.selectById(id);
    }

    @Override
    public ResourceServerRuntimeDO getRuntime(String id) {
        return resourceServerRuntimeMapper.selectById(id);
    }

    @Override
    public PageResult<ResourceServerDO> getPage(ServerLandingPageReqVO reqVO) {
        // 子表 (status / ipType) 过滤先拿候选 serverIds
        Set<String> idIn = null;
        if (StrUtil.isNotBlank(reqVO.getStatus()) || StrUtil.isNotBlank(reqVO.getIpTypeId())) {
            List<ResourceServerLandingDO> rows = resourceServerLandingMapper.selectByFilter(reqVO.getStatus(), reqVO.getIpTypeId());
            idIn = CollectionUtils.convertSet(rows, ResourceServerLandingDO::getServerId);
            if (CollUtil.isEmpty(idIn)) {
                return PageResult.empty();
            }
        }
        // 账单到期升序 (空排末) → 创建倒序: 排序键在账面子表, 走 LEFT JOIN 在库里排 + 分页
        IPage<ResourceServerDO> result = resourceServerMapper.selectLandingPageOrderByExpiry(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()),
                reqVO.getKeyword(), reqVO.getLifecycleState(), reqVO.getRegionCodes(),
                idIn, ResourceServerTypeEnum.LANDING.getState());
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public Map<String, Long> getSummary() {
        Map<String, Long> result = new HashMap<>();
        long total = 0;
        long installing = 0, ready = 0, live = 0, retired = 0;
        long available = 0, occupied = 0, reserved = 0;

        List<ResourceServerDO> servers = resourceServerMapper.selectByServerType(ResourceServerTypeEnum.LANDING.getState());
        total = servers.size();
        for (ResourceServerDO s : servers) {
            String state = s.getLifecycleState();
            if (ResourceServerLifecycleEnum.INSTALLING.matches(state)) installing++;
            else if (ResourceServerLifecycleEnum.READY.matches(state)) ready++;
            else if (ResourceServerLifecycleEnum.LIVE.matches(state)) live++;
            else if (ResourceServerLifecycleEnum.RETIRED.matches(state)) retired++;
        }
        if (CollUtil.isNotEmpty(servers)) {
            List<String> ids = CollectionUtils.convertList(servers, ResourceServerDO::getId);
            List<ResourceServerLandingDO> landings = resourceServerLandingMapper.selectBatchIds(ids);
            for (ResourceServerLandingDO l : landings) {
                ResourceServerLandingStatusEnum st = ResourceServerLandingStatusEnum.fromState(l.getStatus());
                if (ObjectUtil.isNull(st)) continue;
                switch (st) {
                    case AVAILABLE -> available++;
                    case OCCUPIED -> occupied++;
                    case RESERVED -> reserved++;
                }
            }
        }
        result.put("total", total);
        result.put("lifecycle_INSTALLING", installing);
        result.put("lifecycle_READY", ready);
        result.put("lifecycle_LIVE", live);
        result.put("lifecycle_RETIRED", retired);
        result.put("status_AVAILABLE", available);
        result.put("status_OCCUPIED", occupied);
        result.put("status_RESERVED", reserved);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean occupyById(String serverId, String memberUserId) {
        resourceServerValidator.validateExists(serverId);
        // 条件更新(仅 AVAILABLE→OCCUPIED); 影响 0 行 = 被并发抢占, 返 false 由上层换下一台(非异常)
        return resourceServerLandingMapper.markOccupied(serverId, memberUserId, LocalDateTime.now()) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseForRevoke(String serverId) {
        // 退订直接转 "可分配"
        int rows = resourceServerLandingMapper.markAvailable(serverId);
        if (rows == 0) {
            log.warn("[landing-release] markAvailable rows=0 serverId={}", serverId);
        }
    }

    @Override
    public Map<String, ResourceServerDO> getServerMap(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);
    }

    @Override
    public Map<String, ResourceServerLandingDO> getLandingMap(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Map.of();
        List<ResourceServerLandingDO> rows = resourceServerLandingMapper.selectBatchIds(serverIds);
        return CollectionUtils.convertMap(rows, ResourceServerLandingDO::getServerId);
    }

    @Override
    public Map<String, ResourceServerBillingDO> getBillingMap(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerBillingMapper.selectBatchIds(serverIds), ResourceServerBillingDO::getServerId);
    }

    @Override
    public Map<String, ResourceServerQuotaDO> getQuotaMap(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerQuotaMapper.selectBatchIds(serverIds), ResourceServerQuotaDO::getServerId);
    }

    @Override
    public Map<String, ResourceServerRuntimeDO> getRuntimeMap(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(serverIds), ResourceServerRuntimeDO::getServerId);
    }

}
