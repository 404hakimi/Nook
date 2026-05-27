package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerProvisionModeEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCapacityUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerBillingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.system.api.iptype.SystemIpTypeApi;
import com.nook.biz.system.api.iptype.dto.SystemIpTypeRespDTO;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
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
@RequiredArgsConstructor
public class ResourceServerLandingServiceImpl implements ResourceServerLandingService {


    private final ResourceServerMapper resourceServerMapper;
    private final ResourceServerLandingMapper landingMapper;
    private final ResourceServerBillingMapper billingMapper;
    private final ResourceServerCapacityMapper capacityMapper;
    private final ResourceServerRuntimeMapper runtimeMapper;
    private final ResourceServerValidator serverValidator;
    private final ResourceServerLandingValidator landingValidator;
    private final SystemIpTypeApi systemIpTypeApi;

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
        landingMapper.insert(lan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ResourceServerDO srv = serverValidator.validateExists(id);
        landingValidator.validateNoBoundClient(id, srv.getIpAddress());
        resourceServerMapper.deleteById(id);
        log.info("[landing] DELETE id={} ip={}", id, srv.getIpAddress());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCore(String id, ServerLandingCoreUpdateReqVO reqVO) {
        serverValidator.validateExists(id);
        landingValidator.validateExists(id);
        landingValidator.validateIpTypeExists(reqVO.getIpTypeId());
        landingValidator.validateIpAddressUnique(id, reqVO.getIpAddress());

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
        landingMapper.updateBySelective(lanPatch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSocks5(String id, ServerLandingSocks5UpdateReqVO reqVO) {
        serverValidator.validateExists(id);
        landingValidator.validateExists(id);

        ResourceServerLandingDO patch = BeanUtils.toBean(reqVO, ResourceServerLandingDO.class);
        patch.setServerId(id);
        // 密码留空 = 保留原值
        if (StrUtil.isBlank(patch.getSocks5Password())) {
            patch.setSocks5Password(null);
        }
        landingMapper.updateBySelective(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBilling(String id, ServerLandingBillingUpdateReqVO reqVO) {
        serverValidator.validateExists(id);
        LocalDateTime now = LocalDateTime.now();

        ResourceServerBillingDO old = billingMapper.selectById(id);
        if (old == null) {
            ResourceServerBillingDO bill = BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
            bill.setServerId(id);
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);
            billingMapper.insert(bill);
            return;
        }
        ResourceServerBillingDO patch = BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
        patch.setServerId(id);
        billingMapper.updateBySelective(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCapacity(String id, ServerLandingCapacityUpdateReqVO reqVO) {
        serverValidator.validateExists(id);
        // capacity 在 create 时已 insert 占位, 这里只走 update 业务阈值
        capacityMapper.updateQuota(id, reqVO.getMonthlyTrafficGb(), reqVO.getBandwidthLimitMbps(),
                null, reqVO.getQuotaResetPolicy());
    }

    @Override
    public ResourceServerDO getServer(String id) {
        return serverValidator.validateExists(id);
    }

    @Override
    public ResourceServerLandingDO getLanding(String id) {
        return landingValidator.validateExists(id);
    }

    @Override
    public ResourceServerBillingDO getBilling(String id) {
        return billingMapper.selectById(id);
    }

    @Override
    public ResourceServerCapacityDO getCapacity(String id) {
        return capacityMapper.selectById(id);
    }

    @Override
    public ResourceServerRuntimeDO getRuntime(String id) {
        return runtimeMapper.selectById(id);
    }

    @Override
    public PageResult<ResourceServerDO> getPage(ServerLandingPageReqVO reqVO) {
        // 子表 (status / ipTypeId) 过滤先拿候选 serverIds
        Set<String> statusFilterIds = null;
        if (StrUtil.isNotBlank(reqVO.getStatus()) || StrUtil.isNotBlank(reqVO.getIpTypeId())) {
            List<ResourceServerLandingDO> rows = landingMapper.selectByFilter(reqVO.getStatus(), reqVO.getIpTypeId());
            statusFilterIds = CollectionUtils.convertSet(rows, ResourceServerLandingDO::getServerId);
            if (statusFilterIds.isEmpty()) {
                return PageResult.empty();
            }
        }
        IPage<ResourceServerDO> page = resourceServerMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()),
                reqVO.getKeyword(), reqVO.getLifecycleState(), reqVO.getRegion(),
                null, statusFilterIds, ResourceServerTypeEnum.LANDING.getState());
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public Map<String, Long> getSummary() {
        Map<String, Long> result = new HashMap<>();
        long total = 0;
        long installing = 0, ready = 0, live = 0, retired = 0;
        long available = 0, occupied = 0, cooling = 0, reserved = 0;

        List<ResourceServerDO> servers = resourceServerMapper.selectByServerType(ResourceServerTypeEnum.LANDING.getState());
        total = servers.size();
        for (ResourceServerDO s : servers) {
            String state = s.getLifecycleState();
            if (ResourceServerLifecycleEnum.INSTALLING.matches(state)) installing++;
            else if (ResourceServerLifecycleEnum.READY.matches(state)) ready++;
            else if (ResourceServerLifecycleEnum.LIVE.matches(state)) live++;
            else if (ResourceServerLifecycleEnum.RETIRED.matches(state)) retired++;
        }
        if (!servers.isEmpty()) {
            List<String> ids = CollectionUtils.convertList(servers, ResourceServerDO::getId);
            List<ResourceServerLandingDO> landings = landingMapper.selectBatchIds(ids);
            for (ResourceServerLandingDO l : landings) {
                ResourceServerLandingStatusEnum st = ResourceServerLandingStatusEnum.fromState(l.getStatus());
                if (st == null) continue;
                switch (st) {
                    case AVAILABLE -> available++;
                    case OCCUPIED -> occupied++;
                    case COOLING -> cooling++;
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
        result.put("status_COOLING", cooling);
        result.put("status_RESERVED", reserved);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceServerDO occupyById(String serverId, String memberUserId) {
        ResourceServerDO srv = serverValidator.validateExists(serverId);
        int rows = landingMapper.markOccupied(serverId, memberUserId, LocalDateTime.now());
        if (rows != 1) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "landing " + serverId + " 占用失败 (并发被抢或状态不是 AVAILABLE)");
        }
        return srv;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseToCoolingForRevoke(String serverId) {
        ResourceServerLandingDO landing = landingMapper.selectByServerId(serverId);
        if (landing == null) {
            log.warn("[landing-release] 子表不存在 serverId={}", serverId);
            return;
        }
        SystemIpTypeRespDTO type = systemIpTypeApi.getById(landing.getIpTypeId());
        if (type == null || type.getCoolingMinutes() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "落地节点 " + serverId + " 的 ip_type " + landing.getIpTypeId() + " 未配 coolingMinutes");
        }
        LocalDateTime coolingUntil = LocalDateTime.now().plusMinutes(type.getCoolingMinutes());
        int rows = landingMapper.markCooling(serverId, coolingUntil);
        if (rows == 0) {
            log.warn("[landing-release] markCooling rows=0 serverId={}", serverId);
        }
    }

    @Override
    public Map<String, ResourceServerLandingDO> getLandingMap(Collection<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) return Collections.emptyMap();
        List<ResourceServerLandingDO> rows = landingMapper.selectBatchIds(serverIds);
        return CollectionUtils.convertMap(rows, ResourceServerLandingDO::getServerId);
    }

    @Override
    public SubtablesBundle batchLoadSubtables(Collection<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            Map<String, ResourceServerLandingDO> emptyLanding = Collections.emptyMap();
            Map<String, ResourceServerBillingDO> emptyBill = Collections.emptyMap();
            Map<String, ResourceServerCapacityDO> emptyCap = Collections.emptyMap();
            Map<String, ResourceServerRuntimeDO> emptyRt = Collections.emptyMap();
            return new SubtablesBundle(emptyLanding, emptyBill, emptyCap, emptyRt);
        }
        Map<String, ResourceServerLandingDO> landings = CollectionUtils.convertMap(
                landingMapper.selectBatchIds(serverIds), ResourceServerLandingDO::getServerId);
        Map<String, ResourceServerBillingDO> bills = CollectionUtils.convertMap(
                billingMapper.selectBatchIds(serverIds), ResourceServerBillingDO::getServerId);
        Map<String, ResourceServerCapacityDO> caps = CollectionUtils.convertMap(
                capacityMapper.selectBatchIds(serverIds), ResourceServerCapacityDO::getServerId);
        Map<String, ResourceServerRuntimeDO> runtimes = CollectionUtils.convertMap(
                runtimeMapper.selectBatchIds(serverIds), ResourceServerRuntimeDO::getServerId);
        return new SubtablesBundle(landings, bills, caps, runtimes);
    }

}
