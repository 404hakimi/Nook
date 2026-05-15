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
        ipPoolValidator.validateExists(id);
        // 删除 IP 池条目
        resourceIpPoolMapper.deleteById(id);
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
    public void releaseToCooling(String id) {
        ResourceIpPoolDO exist = ipPoolValidator.validateExists(id);
        // 守卫: 有 client 还引用此 IP, 拒绝退订 (避免 client status 与 pool status 漂移);
        // revoke 链路先 deleteById 删 client 行, 再调本方法, 所以不会撞这里.
        XrayClientDO bound = xrayClientMapper.selectByIpId(id);
        if (bound != null) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_HAS_BOUND_CLIENT,
                    exist.getIpAddress(), bound.getMemberUserId());
        }
        // ip_type.cooling_minutes 是 NOT NULL 列; 家宽 IP 通常需要更久
        ResourceIpTypeDO type = resourceIpTypeMapper.selectById(exist.getIpTypeId());
        if (ObjectUtil.isNull(type) || ObjectUtil.isNull(type.getCoolingMinutes())) {
            throw new BusinessException(ResourceErrorCode.IP_TYPE_NOT_FOUND, exist.getIpTypeId());
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
