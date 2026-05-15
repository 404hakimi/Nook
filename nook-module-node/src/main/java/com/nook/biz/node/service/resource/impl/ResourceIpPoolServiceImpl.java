package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.config.ResourceIpPoolProperties;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpTypeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpTypeMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.enums.ResourceErrorCode;
import com.nook.biz.node.enums.ResourceIpPoolStatusEnum;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * IP 池 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceIpPoolServiceImpl implements ResourceIpPoolService {

    private final ResourceIpPoolMapper resourceIpPoolMapper;
    private final ResourceIpTypeMapper resourceIpTypeMapper;
    private final XrayClientMapper xrayClientMapper;
    private final ResourceIpPoolValidator ipPoolValidator;
    private final ResourceIpPoolProperties resourceIpPoolProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createIpPool(ResourceIpPoolSaveReqVO createReqVO) {
        // 校验 IP 类型存在
        ipPoolValidator.validateIpTypeExists(createReqVO.getIpTypeId());
        // 校验 IP 地址唯一
        ipPoolValidator.validateIpAddressUnique(null, createReqVO.getIpAddress());

        // 插入 IP 池条目
        ResourceIpPoolDO entity = BeanUtils.toBean(createReqVO, ResourceIpPoolDO.class);
        resourceIpPoolMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIpPool(String id, ResourceIpPoolSaveReqVO updateReqVO) {
        // 校验 IP 池条目存在
        ipPoolValidator.validateExists(id);
        // 校验 IP 类型存在
        ipPoolValidator.validateIpTypeExists(updateReqVO.getIpTypeId());
        // 校验 IP 地址唯一
        ipPoolValidator.validateIpAddressUnique(id, updateReqVO.getIpAddress());

        // 更新 IP 池条目
        ResourceIpPoolDO updateObj = BeanUtils.toBean(updateReqVO, ResourceIpPoolDO.class);
        resourceIpPoolMapper.update(updateObj, Wrappers.<ResourceIpPoolDO>lambdaUpdate().eq(ResourceIpPoolDO::getId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteIpPool(String id) {
        // 校验 IP 池条目存在
        ResourceIpPoolDO exist = ipPoolValidator.validateExists(id);
        // 守卫: 有 client 绑定时拒绝删除 (跟 releaseToCooling 一致), 避免 client 走孤儿引用
        XrayClientDO bound = xrayClientMapper.selectByIpId(id);
        if (bound != null) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_HAS_BOUND_CLIENT,
                    exist.getIpAddress(), bound.getMemberUserId());
        }
        resourceIpPoolMapper.deleteById(id);
        log.info("[ip-pool] DELETE ipId={} ip={}", id, exist.getIpAddress());
    }

    @Override
    public ResourceIpPoolDO getIpPool(String id) {
        return resourceIpPoolMapper.selectById(id);
    }

    @Override
    public PageResult<ResourceIpPoolDO> getIpPoolPage(ResourceIpPoolPageReqVO pageReqVO) {
        IPage<ResourceIpPoolDO> result = resourceIpPoolMapper.selectPageByQuery(
                Page.of(pageReqVO.getPageNo(), pageReqVO.getPageSize()), pageReqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceIpPoolDO occupyOne(String region, String ipTypeId, String memberUserId) {
        // 防并发双卖: selectAvailable 拿到候选 → markOccupied(WHERE status=AVAILABLE) 原子改; 0 行受影响 = 被别人抢了, 重试
        String lastIp = "";
        int maxRetry = resourceIpPoolProperties.getOccupyRetry();
        for (int i = 0; i <= maxRetry; i++) {
            ResourceIpPoolDO candidate = resourceIpPoolMapper.selectAvailable(region, ipTypeId);
            if (ObjectUtil.isNull(candidate)) {
                throw new BusinessException(ResourceErrorCode.IP_POOL_EXHAUSTED,
                        StrUtil.blankToDefault(region, "*"), StrUtil.blankToDefault(ipTypeId, "*"));
            }
            lastIp = candidate.getIpAddress();
            int affected = resourceIpPoolMapper.markOccupied(candidate.getId(), memberUserId, LocalDateTime.now());
            if (affected > 0) {
                // 内存对象同步 DB 状态, 上游不再二次查
                candidate.setStatus(ResourceIpPoolStatusEnum.OCCUPIED.getStatus());
                candidate.setAssignedMemberId(memberUserId);
                candidate.setAssignedAt(LocalDateTime.now());
                candidate.setAssignCount(candidate.getAssignCount() + 1);
                return candidate;
            }
        }
        throw new BusinessException(ResourceErrorCode.IP_POOL_OCCUPY_CONFLICT, lastIp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceIpPoolDO occupyById(String id, String memberUserId) {
        // 校验存在 + 拿原行 (含 ipAddress / 当前 status, 错误信息要用)
        ResourceIpPoolDO exist = ipPoolValidator.validateExists(id);
        LocalDateTime now = LocalDateTime.now();
        // markOccupied 自带 WHERE status=AVAILABLE 防双卖; 0 行 = 当前已不是 available
        int affected = resourceIpPoolMapper.markOccupied(id, memberUserId, now);
        if (affected == 0) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_NOT_AVAILABLE,
                    exist.getIpAddress(), exist.getStatus());
        }
        // 内存对象同步 DB 状态, 上游不再二次查
        exist.setStatus(ResourceIpPoolStatusEnum.OCCUPIED.getStatus());
        exist.setAssignedMemberId(memberUserId);
        exist.setAssignedAt(now);
        int newCount = (exist.getAssignCount() == null ? 0 : exist.getAssignCount()) + 1;
        exist.setAssignCount(newCount);
        log.info("[ip-pool] OCCUPY ipId={} ip={} member={} count={}",
                id, exist.getIpAddress(), memberUserId, newCount);
        return exist;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseToCoolingForRevoke(String id) {
        // revoke 链路: 跟外层 (revokeDbOnly) 同事务, 看得到 deleteById 删的 client; 不做 bound 检查.
        doMarkCooling(id, /* checkBound= */ false);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void releaseToCooling(String id) {
        // user-facing: 独立事务 + 带 bound 守卫. doMarkCooling 抛错时 REQUIRES_NEW 自身 rollback 不污染外层
        doMarkCooling(id, /* checkBound= */ true);
    }

    /** releaseToCooling 与 releaseToCoolingForRevoke 共用核心逻辑; checkBound 控制是否做 bound 守卫. */
    private void doMarkCooling(String id, boolean checkBound) {
        // 软存在校验: IP 已被删 → 没什么可退订的, no-op 返回; 比 validateExists 抛错更友好 (revoke 场景常见)
        ResourceIpPoolDO exist = resourceIpPoolMapper.selectById(id);
        if (exist == null) {
            log.info("[ip-pool] RELEASE skip ipId={} (IP 行已不存在, 无需操作)", id);
            return;
        }
        // 守卫: 有 client 还引用此 IP, 拒绝退订 (避免 client status 与 pool status 漂移);
        // revoke 链路在外层事务先 deleteById 删 client; 若走 REQUIRES_NEW 看不到外层未 commit 的 delete,
        // 反而会误判 bound != null 把 release 拒了 → IP 卡在 occupied. 因此 revoke 路径 checkBound=false 跳过.
        if (checkBound) {
            XrayClientDO bound = xrayClientMapper.selectByIpId(id);
            if (bound != null) {
                throw new BusinessException(ResourceErrorCode.IP_POOL_HAS_BOUND_CLIENT,
                        exist.getIpAddress(), bound.getMemberUserId());
            }
        }
        // ip_type.cooling_minutes 是 NOT NULL 列; 家宽 IP 通常需要更久
        ResourceIpTypeDO type = resourceIpTypeMapper.selectById(exist.getIpTypeId());
        if (ObjectUtil.isNull(type) || ObjectUtil.isNull(type.getCoolingMinutes())) {
            // ip_type 也丢了说明数据严重错位; 不强抛阻断 revoke, 仅 warn 让运维处理
            log.warn("[ip-pool] RELEASE ipId={} ip={} 找不到 ip_type={}, 跳过 cooling 切换",
                    id, exist.getIpAddress(), exist.getIpTypeId());
            return;
        }
        resourceIpPoolMapper.markCooling(exist.getId(),
                LocalDateTime.now().plusMinutes(type.getCoolingMinutes()));
        log.info("[ip-pool] RELEASE ipId={} ip={} cooling={}min",
                id, exist.getIpAddress(), type.getCoolingMinutes());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int sweepExpiredCooling() {
        int n = 0;
        for (ResourceIpPoolDO ip : resourceIpPoolMapper.selectCoolingExpired(LocalDateTime.now())) {
            int aff = resourceIpPoolMapper.markAvailable(ip.getId());
            n += aff;
            if (aff > 0) {
                log.info("[ip-pool] SWEEP-AVAILABLE ipId={} ip={}", ip.getId(), ip.getIpAddress());
            }
        }
        return n;
    }

    @Override
    public Map<String, String> getIpAddressMap(Collection<String> ids) {
        if (CollectionUtils.isAnyEmpty(ids)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                resourceIpPoolMapper.selectBatchIds(ids),
                ResourceIpPoolDO::getId,
                ResourceIpPoolDO::getIpAddress);
    }
}
