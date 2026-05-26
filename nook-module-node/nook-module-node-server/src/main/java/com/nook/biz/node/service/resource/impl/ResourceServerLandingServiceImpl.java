package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCapacityUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCreateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCredentialUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerBillingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.service.resource.ResourceServerService;
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

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final ResourceServerCredentialMapper credentialMapper;
    private final ResourceServerBillingMapper billingMapper;
    private final ResourceServerCapacityMapper capacityMapper;
    private final ResourceServerRuntimeMapper runtimeMapper;
    private final ResourceServerValidator serverValidator;
    private final ResourceServerLandingValidator landingValidator;
    private final ResourceServerService resourceServerService;
    private final SystemIpTypeApi systemIpTypeApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ServerLandingCreateReqVO reqVO) {
        landingValidator.validateIpTypeExists(reqVO.getIpTypeId());
        landingValidator.validateIpAddressUnique(null, reqVO.getIpAddress());

        // 主表 (server_type/name 由后端决定的身份字段; agentToken 一次性签发; 其余从 reqVO 拷贝)
        ResourceServerDO srv = BeanUtils.toBean(reqVO, ResourceServerDO.class);
        srv.setServerType(ResourceServerTypeEnum.LANDING.getState());
        srv.setName(landingName(reqVO.getIpAddress()));
        srv.setAgentToken(generateAgentToken());
        serverValidator.validateNameUnique(null, srv.getName());
        resourceServerMapper.insert(srv);

        LocalDateTime now = LocalDateTime.now();

        // landing 子表 (status/assignCount 是新行 initial state; 其余从 reqVO 拷贝)
        ResourceServerLandingDO lan = BeanUtils.toBean(reqVO, ResourceServerLandingDO.class);
        lan.setServerId(srv.getId());
        lan.setStatus(ResourceServerLandingStatusEnum.AVAILABLE.getState());
        lan.setAssignCount(0);
        lan.setCreatedAt(now);
        lan.setUpdatedAt(now);
        landingMapper.insert(lan);

        // credential 子表 (sshUser/sshPassword 任一非空就插); host 已不在凭据, 走 server.ip_address
        if (StrUtil.isNotBlank(reqVO.getSshUser()) || StrUtil.isNotBlank(reqVO.getSshPassword())) {
            ResourceServerCredentialDO cred = new ResourceServerCredentialDO();
            cred.setServerId(srv.getId());
            cred.setSshPort(reqVO.getSshPort());
            cred.setSshUser(reqVO.getSshUser());
            cred.setSshPassword(reqVO.getSshPassword());
            cred.setSshTimeoutSeconds(reqVO.getSshTimeoutSeconds());
            cred.setSshOpTimeoutSeconds(reqVO.getSshOpTimeoutSeconds());
            cred.setSshUploadTimeoutSeconds(reqVO.getSshUploadTimeoutSeconds());
            cred.setInstallTimeoutSeconds(reqVO.getInstallTimeoutSeconds());
            cred.setCreatedAt(now);
            cred.setUpdatedAt(now);
            credentialMapper.insert(cred);
        }

        // billing 子表 (有任一字段就插)
        if (reqVO.getCostMonthlyUsd() != null || reqVO.getBillingCycleDay() != null || reqVO.getExpiresAt() != null) {
            ResourceServerBillingDO bill = BeanUtils.toBean(reqVO, ResourceServerBillingDO.class);
            bill.setServerId(srv.getId());
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);
            billingMapper.insert(bill);
        }

        // capacity / runtime 占位 (initial state)
        ResourceServerCapacityDO cap = new ResourceServerCapacityDO();
        cap.setServerId(srv.getId());
        cap.setRxBytes(0L);
        cap.setTxBytes(0L);
        cap.setUsedTrafficBytes(0L);
        cap.setCreatedAt(now);
        cap.setUpdatedAt(now);
        capacityMapper.insert(cap);

        ResourceServerRuntimeDO runtime = new ResourceServerRuntimeDO();
        runtime.setServerId(srv.getId());
        runtime.setTempUnhealthy(0);
        runtime.setConsecutiveMiss(0);
        runtime.setUpdatedAt(now);
        runtimeMapper.insert(runtime);

        log.info("[landing] CREATE id={} ip={} region={}", srv.getId(), reqVO.getIpAddress(), reqVO.getRegion());
        return srv.getId();
    }

    private static String landingName(String ipAddress) {
        return "landing-" + ipAddress;
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
    public void updateCredential(String id, ServerLandingCredentialUpdateReqVO reqVO) {
        serverValidator.validateExists(id);
        LocalDateTime now = LocalDateTime.now();

        ResourceServerCredentialDO old = credentialMapper.selectById(id);
        if (old == null) {
            ResourceServerCredentialDO cred = new ResourceServerCredentialDO();
            cred.setServerId(id);
            cred.setSshPort(reqVO.getSshPort());
            cred.setSshUser(reqVO.getSshUser());
            cred.setSshPassword(reqVO.getSshPassword());
            cred.setCreatedAt(now);
            cred.setUpdatedAt(now);
            credentialMapper.insert(cred);
            return;
        }
        ResourceServerCredentialDO patch = new ResourceServerCredentialDO();
        patch.setServerId(id);
        patch.setSshPort(reqVO.getSshPort());
        patch.setSshUser(reqVO.getSshUser());
        // 密码留空 = 保留原值
        if (StrUtil.isNotBlank(reqVO.getSshPassword())) {
            patch.setSshPassword(reqVO.getSshPassword());
        }
        credentialMapper.updateBySelective(patch);
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
    public ResourceServerCredentialDO getCredential(String id) {
        return credentialMapper.selectById(id);
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
    public void transitionLifecycle(String id, String state) {
        resourceServerService.transitionLifecycle(id, state);
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
            Map<String, ResourceServerCredentialDO> emptyCred = Collections.emptyMap();
            Map<String, ResourceServerBillingDO> emptyBill = Collections.emptyMap();
            Map<String, ResourceServerCapacityDO> emptyCap = Collections.emptyMap();
            Map<String, ResourceServerRuntimeDO> emptyRt = Collections.emptyMap();
            return new SubtablesBundle(emptyLanding, emptyCred, emptyBill, emptyCap, emptyRt);
        }
        Map<String, ResourceServerLandingDO> landings = CollectionUtils.convertMap(
                landingMapper.selectBatchIds(serverIds), ResourceServerLandingDO::getServerId);
        Map<String, ResourceServerCredentialDO> creds = CollectionUtils.convertMap(
                credentialMapper.selectBatchIds(serverIds), ResourceServerCredentialDO::getServerId);
        Map<String, ResourceServerBillingDO> bills = CollectionUtils.convertMap(
                billingMapper.selectBatchIds(serverIds), ResourceServerBillingDO::getServerId);
        Map<String, ResourceServerCapacityDO> caps = CollectionUtils.convertMap(
                capacityMapper.selectBatchIds(serverIds), ResourceServerCapacityDO::getServerId);
        Map<String, ResourceServerRuntimeDO> runtimes = CollectionUtils.convertMap(
                runtimeMapper.selectBatchIds(serverIds), ResourceServerRuntimeDO::getServerId);
        return new SubtablesBundle(landings, creds, bills, caps, runtimes);
    }

    /** SHA256(UUID + UUID) → 64 char hex. */
    private static String generateAgentToken() {
        String raw = UUID.randomUUID() + UUID.randomUUID().toString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes()));
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "SHA-256 不可用: " + e.getMessage());
        }
    }
}
