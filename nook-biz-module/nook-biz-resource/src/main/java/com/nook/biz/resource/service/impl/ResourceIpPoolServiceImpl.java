package com.nook.biz.resource.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.resource.constant.ResourceErrorCode;
import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.resource.entity.ResourceIpPool;
import com.nook.biz.resource.entity.ResourceIpType;
import com.nook.biz.resource.mapper.ResourceIpPoolMapper;
import com.nook.biz.resource.mapper.ResourceIpTypeMapper;
import com.nook.biz.resource.service.ResourceIpPoolService;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ResourceIpPoolServiceImpl implements ResourceIpPoolService {

    /** 抢占失败重试次数; SELECT-then-UPDATE 防双卖偶尔会与并发兑换抢同一行, 这是技术常量不是业务参数。 */
    private static final int OCCUPY_RETRY = 2;

    private final ResourceIpPoolMapper resourceIpPoolMapper;
    private final ResourceIpTypeMapper resourceIpTypeMapper;

    @Override
    public ResourceIpPool findById(String id) {
        ResourceIpPool e = resourceIpPoolMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_NOT_FOUND, id);
        }
        return e;
    }

    @Override
    public PageResult<ResourceIpPool> page(ResourceIpPoolPageReqVO reqVO) {
        IPage<ResourceIpPool> result = resourceIpPoolMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public ResourceIpPool create(ResourceIpPoolSaveReqVO reqVO) {
        validateIpTypeExists(reqVO.getIpTypeId());
        if (resourceIpPoolMapper.selectByIpAddress(reqVO.getIpAddress()) != null) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_IP_DUPLICATE, reqVO.getIpAddress());
        }
        // status / score / assign_count 在 DB 是 NOT NULL DEFAULT, 上层不传时 MyBatis-Plus 默认策略
        // 会跳过 null 列让 DB 兜默认值; 业务侧不再 fallback。
        ResourceIpPool e = new ResourceIpPool();
        e.setRegion(reqVO.getRegion());
        e.setIpTypeId(reqVO.getIpTypeId());
        e.setIpAddress(reqVO.getIpAddress());
        e.setSocks5Host(reqVO.getSocks5Host());
        e.setSocks5Port(reqVO.getSocks5Port());
        e.setSocks5Username(reqVO.getSocks5Username());
        e.setSocks5Password(reqVO.getSocks5Password());
        e.setStatus(reqVO.getStatus());
        e.setScore(reqVO.getScore());
        e.setScamalyticsScore(reqVO.getScamalyticsScore());
        e.setIpqsScore(reqVO.getIpqsScore());
        e.setRemark(reqVO.getRemark());
        resourceIpPoolMapper.insert(e);
        return e;
    }

    @Override
    public ResourceIpPool update(String id, ResourceIpPoolSaveReqVO reqVO) {
        ResourceIpPool exist = findById(id);
        if (StrUtil.isNotBlank(reqVO.getIpTypeId())) {
            validateIpTypeExists(reqVO.getIpTypeId());
        }
        if (StrUtil.isNotBlank(reqVO.getIpAddress())
                && !StrUtil.equals(reqVO.getIpAddress(), exist.getIpAddress())
                && resourceIpPoolMapper.existsByIpAddressExcludingId(reqVO.getIpAddress(), id)) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_IP_DUPLICATE, reqVO.getIpAddress());
        }
        // 留空 = 保留原值; 非空才覆盖。
        if (StrUtil.isNotBlank(reqVO.getRegion())) exist.setRegion(reqVO.getRegion());
        if (StrUtil.isNotBlank(reqVO.getIpTypeId())) exist.setIpTypeId(reqVO.getIpTypeId());
        if (StrUtil.isNotBlank(reqVO.getIpAddress())) exist.setIpAddress(reqVO.getIpAddress());
        if (StrUtil.isNotBlank(reqVO.getSocks5Host())) exist.setSocks5Host(reqVO.getSocks5Host());
        if (ObjectUtil.isNotNull(reqVO.getSocks5Port())) exist.setSocks5Port(reqVO.getSocks5Port());
        if (StrUtil.isNotBlank(reqVO.getSocks5Username())) exist.setSocks5Username(reqVO.getSocks5Username());
        if (StrUtil.isNotBlank(reqVO.getSocks5Password())) exist.setSocks5Password(reqVO.getSocks5Password());
        if (ObjectUtil.isNotNull(reqVO.getStatus())) exist.setStatus(reqVO.getStatus());
        if (ObjectUtil.isNotNull(reqVO.getScore())) exist.setScore(reqVO.getScore());
        if (ObjectUtil.isNotNull(reqVO.getScamalyticsScore())) exist.setScamalyticsScore(reqVO.getScamalyticsScore());
        if (ObjectUtil.isNotNull(reqVO.getIpqsScore())) exist.setIpqsScore(reqVO.getIpqsScore());
        if (StrUtil.isNotBlank(reqVO.getRemark())) exist.setRemark(reqVO.getRemark());
        resourceIpPoolMapper.updateById(exist);
        return exist;
    }

    @Override
    public void delete(String id) {
        ResourceIpPool exist = findById(id);
        resourceIpPoolMapper.deleteById(exist.getId());
    }

    @Override
    public ResourceIpPool occupyOne(String region, String ipTypeId, String memberUserId) {
        // 防并发双卖: selectAvailable 拿到候选 → markOccupied(WHERE status=1) 原子改; 0 行受影响 = 被别人抢了, 重试。
        String lastIp = "";
        for (int i = 0; i <= OCCUPY_RETRY; i++) {
            ResourceIpPool candidate = resourceIpPoolMapper.selectAvailable(region, ipTypeId);
            if (candidate == null) {
                throw new BusinessException(ResourceErrorCode.IP_POOL_EXHAUSTED,
                        StrUtil.blankToDefault(region, "*"), StrUtil.blankToDefault(ipTypeId, "*"));
            }
            lastIp = candidate.getIpAddress();
            int affected = resourceIpPoolMapper.markOccupied(candidate.getId(), memberUserId, LocalDateTime.now());
            if (affected > 0) {
                // 回填实际写入字段; 减少调用方再 selectById。
                candidate.setStatus(2);
                candidate.setAssignedMemberId(memberUserId);
                candidate.setAssignedAt(LocalDateTime.now());
                candidate.setAssignCount(candidate.getAssignCount() + 1);
                return candidate;
            }
        }
        throw new BusinessException(ResourceErrorCode.IP_POOL_OCCUPY_CONFLICT, lastIp);
    }

    @Override
    public void releaseToCooling(String id) {
        ResourceIpPool exist = findById(id);
        // 冷却时长按 IP 类型可配 (家宽 IP 通常需要更久); ip_type.cooling_minutes 是 NOT NULL 列
        ResourceIpType type = resourceIpTypeMapper.selectById(exist.getIpTypeId());
        if (ObjectUtil.isNull(type) || ObjectUtil.isNull(type.getCoolingMinutes())) {
            throw new BusinessException(ResourceErrorCode.IP_TYPE_NOT_FOUND, exist.getIpTypeId());
        }
        resourceIpPoolMapper.markCooling(exist.getId(),
                LocalDateTime.now().plusMinutes(type.getCoolingMinutes()));
    }

    @Override
    public int sweepExpiredCooling() {
        int n = 0;
        for (ResourceIpPool ip : resourceIpPoolMapper.selectCoolingExpired(LocalDateTime.now())) {
            n += resourceIpPoolMapper.markAvailable(ip.getId());
        }
        return n;
    }

    @Override
    public void updateSocks5(String ipId, String socks5Host, Integer socks5Port, String socks5Username, String socks5Password) {
        ResourceIpPool exist = findById(ipId);
        if (StrUtil.isNotBlank(socks5Host)) exist.setSocks5Host(socks5Host);
        if (ObjectUtil.isNotNull(socks5Port)) exist.setSocks5Port(socks5Port);
        if (StrUtil.isNotBlank(socks5Username)) exist.setSocks5Username(socks5Username);
        if (StrUtil.isNotBlank(socks5Password)) exist.setSocks5Password(socks5Password);
        resourceIpPoolMapper.updateById(exist);
    }

    /** 引用 ip_type.id 必须存在; 录入/编辑时校验。 */
    private void validateIpTypeExists(String ipTypeId) {
        ResourceIpType type = resourceIpTypeMapper.selectById(ipTypeId);
        if (ObjectUtil.isNull(type)) {
            throw new BusinessException(ResourceErrorCode.IP_TYPE_NOT_FOUND, ipTypeId);
        }
    }
}
