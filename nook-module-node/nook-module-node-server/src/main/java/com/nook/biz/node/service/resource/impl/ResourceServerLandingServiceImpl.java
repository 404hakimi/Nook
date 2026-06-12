package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerProvisionModeEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingQuotaUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingListItemRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.entity.ResourceServerBillingDO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.entity.ResourceServerRuntimeDO;
import com.nook.biz.node.mapper.ResourceServerBillingMapper;
import com.nook.biz.node.mapper.ResourceServerQuotaMapper;
import com.nook.biz.node.mapper.Socks5InstallMapper;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.convert.resource.ResourceServerLandingConvert;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.ServerLifecycleValidator;
import com.nook.biz.trade.api.SubscriptionCertApi;
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
    private Socks5InstallMapper socks5InstallMapper;
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
    @Resource
    private ServerLifecycleValidator serverLifecycleValidator;
    @Resource
    private SubscriptionCertApi subscriptionCertApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initSubtables(String serverId, String ipTypeId) {
        // 只填业务必填字段; SOCKS5 凭据 / dante install 字段留 null, 由"部署"流程前端 prefill 后写入
        LocalDateTime now = LocalDateTime.now();
        Socks5InstallDO lan = new Socks5InstallDO();
        lan.setServerId(serverId);
        lan.setIpTypeId(ipTypeId);
        lan.setProvisionMode(ResourceServerProvisionModeEnum.SELF_DEPLOY.getCode());
        lan.setCreatedAt(now);
        lan.setUpdatedAt(now);
        socks5InstallMapper.insert(lan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCore(String id, ServerLandingCoreUpdateReqVO reqVO) {
        // 校验存在 + IP 类型 / IP 唯一 / 部署模式取值 + 区域可改性 (上线后锁定)
        ResourceServerDO existing = resourceServerValidator.validateExists(id);
        resourceServerLandingValidator.validateExists(id);
        resourceServerLandingValidator.validateIpTypeExists(reqVO.getIpTypeId());
        resourceServerLandingValidator.validateIpAddressUnique(id, reqVO.getIpAddress());
        resourceServerLandingValidator.validateProvisionMode(reqVO.getProvisionMode());
        resourceServerValidator.validateRegionMutable(existing, reqVO.getRegion());
        // 主表与 landing 子表分别更新
        ResourceServerDO srvPatch = new ResourceServerDO();
        srvPatch.setId(id);
        srvPatch.setRegion(reqVO.getRegion());
        srvPatch.setIpAddress(reqVO.getIpAddress());
        srvPatch.setRemark(reqVO.getRemark());
        resourceServerMapper.updateById(srvPatch);

        Socks5InstallDO lanPatch = new Socks5InstallDO();
        lanPatch.setServerId(id);
        lanPatch.setIpTypeId(reqVO.getIpTypeId());
        lanPatch.setProvisionMode(reqVO.getProvisionMode());
        socks5InstallMapper.updateBySelective(lanPatch);
    }

    @Override
    public void updateSocks5(String id, ServerLandingSocks5UpdateReqVO reqVO) {
        // 校验存在
        resourceServerValidator.validateExists(id);
        resourceServerLandingValidator.validateExists(id);
        // 更新 dante 配置; 密码留空 = 保留原值
        Socks5InstallDO patch = BeanUtils.toBean(reqVO, Socks5InstallDO.class);
        patch.setServerId(id);
        if (StrUtil.isBlank(patch.getSocks5Password())) {
            patch.setSocks5Password(null);
        }
        socks5InstallMapper.updateBySelective(patch);
    }

    @Override
    public void updateBilling(String id, ServerLandingBillingUpdateReqVO reqVO) {
        // 校验存在
        resourceServerValidator.validateExists(id);
        // 账面子表不存在则插入, 否则按非空字段更新
        ResourceServerBillingDO old = resourceServerBillingMapper.selectById(id);
        if (ObjectUtil.isNull(old)) {
            LocalDateTime now = LocalDateTime.now();
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
    public void updateQuota(String id, ServerLandingQuotaUpdateReqVO reqVO) {
        // 校验存在
        resourceServerValidator.validateExists(id);
        // 配额配置行装机时已 insert 占位, 这里只走 update 上限
        resourceServerQuotaMapper.updateQuota(id, reqVO.getTotalGb(), reqVO.getUsablePercent(),
                reqVO.getBandwidthMbps(), reqVO.getResetPolicy(), reqVO.getResetDay());
    }

    @Override
    public ResourceServerDO getServer(String id) {
        return resourceServerValidator.validateExists(id);
    }

    @Override
    public Socks5InstallDO getLanding(String id) {
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
    public PageResult<ServerLandingListItemRespVO> getLandingPage(ServerLandingPageReqVO reqVO) {
        // 主表 + landing/billing/quota/runtime 连表按需出列表项; status/ipType 直接 JOIN landing 子表过滤
        IPage<ServerLandingListItemRespVO> result = resourceServerMapper.selectLandingPage(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()),
                reqVO.getKeyword(), reqVO.getLifecycleState(), reqVO.getIpTypeId(),
                reqVO.getRegionCodes(), ResourceServerTypeEnum.LANDING.getState());
        LocalDateTime now = LocalDateTime.now();
        result.getRecords().forEach(vo -> ResourceServerLandingConvert.fillOnlineState(vo, now));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public Map<String, Long> getSummary() {
        Map<String, Long> result = new HashMap<>();
        long installing = 0, ready = 0, live = 0, retired = 0;

        List<ResourceServerDO> servers = resourceServerMapper.selectByServerType(ResourceServerTypeEnum.LANDING.getState());
        long total = servers.size();
        for (ResourceServerDO s : servers) {
            String state = s.getLifecycleState();
            if (ResourceServerLifecycleEnum.INSTALLING.matches(state)) installing++;
            else if (ResourceServerLifecycleEnum.READY.matches(state)) ready++;
            else if (ResourceServerLifecycleEnum.LIVE.matches(state)) live++;
            else if (ResourceServerLifecycleEnum.RETIRED.matches(state)) retired++;
        }
        // 占用由 cert.ip_id 派生 (含 ACTIVE/SUSPENDED); 可用 = 总数 - 占用
        long occupied = subscriptionCertApi.filterBoundIpIds(
                CollectionUtils.convertList(servers, ResourceServerDO::getId)).size();
        result.put("total", total);
        result.put("lifecycle_INSTALLING", installing);
        result.put("lifecycle_READY", ready);
        result.put("lifecycle_LIVE", live);
        result.put("lifecycle_RETIRED", retired);
        result.put("status_AVAILABLE", total - occupied);
        result.put("status_OCCUPIED", occupied);
        return result;
    }

    @Override
    public Map<String, ResourceServerDO> getServerMap(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Map.of();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);
    }

    @Override
    public Map<String, Socks5InstallDO> getLandingMap(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Map.of();
        List<Socks5InstallDO> rows = socks5InstallMapper.selectBatchIds(serverIds);
        return CollectionUtils.convertMap(rows, Socks5InstallDO::getServerId);
    }

    @Override
    public void transitionLifecycle(String id, String newState) {
        // 校验存在; 状态未变直接幂等返回
        ResourceServerDO srv = resourceServerValidator.validateExists(id);
        if (StrUtil.equals(srv.getLifecycleState(), newState)) {
            return;
        }
        // 校验流转表; 停用另查未被客户端占用
        serverLifecycleValidator.validateTransitionTable(srv, newState);
        if (ResourceServerLifecycleEnum.RETIRED.matches(newState)) {
            serverLifecycleValidator.validateLandingNotInUse(id);
        }
        // 落新状态
        resourceServerMapper.updateLifecycleState(id, newState);
        log.info("[transitionLifecycle] 落地机生命周期切换: id={}, {} → {}", id, srv.getLifecycleState(), newState);
    }

}
