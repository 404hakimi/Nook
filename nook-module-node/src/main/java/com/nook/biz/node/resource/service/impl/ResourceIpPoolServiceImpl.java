package com.nook.biz.node.resource.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.resource.constant.ResourceErrorCode;
import com.nook.biz.node.resource.controller.ip.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.resource.controller.ip.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.node.resource.entity.ResourceIpPool;
import com.nook.biz.node.resource.entity.ResourceIpType;
import com.nook.biz.node.resource.mapper.ResourceIpPoolMapper;
import com.nook.biz.node.resource.mapper.ResourceIpTypeMapper;
import com.nook.biz.node.resource.service.ResourceIpPoolService;
import com.nook.biz.node.resource.validator.ResourceIpPoolValidator;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceIpPoolServiceImpl implements ResourceIpPoolService {

    /** 抢占失败重试次数; SELECT-then-UPDATE 防双卖偶尔会与并发兑换抢同一行, 这是技术常量不是业务参数. */
    private static final int OCCUPY_RETRY = 2;

    private final ResourceIpPoolMapper resourceIpPoolMapper;
    private final ResourceIpTypeMapper resourceIpTypeMapper;
    private final ResourceIpPoolValidator ipPoolValidator;

    @Override
    public ResourceIpPool findById(String id) {
        return ipPoolValidator.validateExists(id);
    }

    @Override
    public PageResult<ResourceIpPool> page(ResourceIpPoolPageReqVO reqVO) {
        IPage<ResourceIpPool> result = resourceIpPoolMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public ResourceIpPool create(ResourceIpPoolSaveReqVO reqVO) {
        // 校验 IP 类型存在
        ipPoolValidator.validateIpTypeExists(reqVO.getIpTypeId());
        // 校验 IP 地址唯一
        ipPoolValidator.validateIpAddressUnique(null, reqVO.getIpAddress());

        // 插入 IP 池条目
        ResourceIpPool entity = BeanUtils.toBean(reqVO, ResourceIpPool.class);
        resourceIpPoolMapper.insert(entity);
        return entity;
    }

    @Override
    public void update(String id, ResourceIpPoolSaveReqVO reqVO) {
        // 校验 IP 池条目存在
        ipPoolValidator.validateExists(id);
        // 校验 IP 类型存在
        ipPoolValidator.validateIpTypeExists(reqVO.getIpTypeId());
        // 校验 IP 地址唯一
        ipPoolValidator.validateIpAddressUnique(id, reqVO.getIpAddress());

        // 更新 IP 池条目
        ResourceIpPool entity = BeanUtils.toBean(reqVO, ResourceIpPool.class);
        resourceIpPoolMapper.update(entity, Wrappers.<ResourceIpPool>lambdaUpdate().eq(ResourceIpPool::getId, id));
    }

    @Override
    public void delete(String id) {
        // 校验 IP 池条目存在
        ipPoolValidator.validateExists(id);
        // 删除 IP 池条目
        resourceIpPoolMapper.deleteById(id);
    }

    @Override
    public ResourceIpPool occupyOne(String region, String ipTypeId, String memberUserId) {
        // 防并发双卖: selectAvailable 拿到候选 → markOccupied(WHERE status=1) 原子改; 0 行受影响 = 被别人抢了, 重试
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
    public ResourceIpPool occupyById(String id, String memberUserId) {
        // exist 校验 + 拿到原行 (含 ipAddress / 当前 status, 错误信息要用)
        ResourceIpPool exist = ipPoolValidator.validateExists(id);
        LocalDateTime now = LocalDateTime.now();
        // markOccupied 自带 WHERE status=1 防双卖; 0 行 = 当前已不是 available
        int affected = resourceIpPoolMapper.markOccupied(id, memberUserId, now);
        if (affected == 0) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_NOT_AVAILABLE,
                    exist.getIpAddress(), exist.getStatus());
        }
        // 内存对象同步反映 DB 更新, 方便上游 toDto 不再多查一次
        exist.setStatus(2);
        exist.setAssignedMemberId(memberUserId);
        exist.setAssignedAt(now);
        exist.setAssignCount((exist.getAssignCount() == null ? 0 : exist.getAssignCount()) + 1);
        return exist;
    }

    @Override
    public void releaseToCooling(String id) {
        ResourceIpPool exist = ipPoolValidator.validateExists(id);
        // ip_type.cooling_minutes 是 NOT NULL 列; 家宽 IP 通常需要更久
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
    public Map<String, String> loadIpAddressMap(Collection<String> ipIds) {
        if (ipIds == null || ipIds.isEmpty()) return Collections.emptyMap();
        Set<String> dedup = new HashSet<>();
        for (String id : ipIds) {
            if (id != null && !id.isEmpty()) dedup.add(id);
        }
        if (dedup.isEmpty()) return Collections.emptyMap();
        List<ResourceIpPool> rows = resourceIpPoolMapper.selectBatchIds(dedup);
        Map<String, String> out = new HashMap<>(rows.size() * 2);
        for (ResourceIpPool e : rows) {
            out.put(e.getId(), e.getIpAddress());
        }
        return out;
    }
}
