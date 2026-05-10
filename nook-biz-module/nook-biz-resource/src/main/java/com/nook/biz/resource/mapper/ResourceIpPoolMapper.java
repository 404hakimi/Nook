package com.nook.biz.resource.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.resource.entity.ResourceIpPool;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IP 池 Mapper.
 * 状态机相关查询:
 *   - 兑换流挑 IP → selectAvailable(region, ipTypeId)
 *   - 退订冷却期到了 → selectCoolingExpired(now) 然后批量回 available
 *   - 健康检查 → selectAllForHealth() 等
 */
@Mapper
public interface ResourceIpPoolMapper extends BaseMapper<ResourceIpPool> {

    /**
     * 按 region + ip_type_id 找一个可分配的 IP.
     * 优先 assign_count 低(尽量轮换避免某 IP 一直被同人用).
     * @return null 表示池子里没货
     */
    default ResourceIpPool selectAvailable(String region, String ipTypeId) {
        return selectOne(Wrappers.<ResourceIpPool>lambdaQuery()
                .eq(ResourceIpPool::getStatus, 1) // available
                .eq(StrUtil.isNotBlank(region), ResourceIpPool::getRegion, region)
                .eq(StrUtil.isNotBlank(ipTypeId), ResourceIpPool::getIpTypeId, ipTypeId)
                .orderByAsc(ResourceIpPool::getAssignCount)
                .last("LIMIT 1"));
    }

    /** 按 ip_address 唯一查; 录入时查重用. */
    default ResourceIpPool selectByIpAddress(String ipAddress) {
        return selectOne(Wrappers.<ResourceIpPool>lambdaQuery()
                .eq(ResourceIpPool::getIpAddress, ipAddress)
                .last("LIMIT 1"));
    }

    /** ip_address + 排除指定 id 是否重复(更新时查重). */
    default boolean existsByIpAddressExcludingId(String ipAddress, String excludeId) {
        return exists(Wrappers.<ResourceIpPool>lambdaQuery()
                .eq(ResourceIpPool::getIpAddress, ipAddress)
                .ne(ResourceIpPool::getId, excludeId));
    }

    /** 找冷却期已到、可以回到 available 的 IP. */
    default List<ResourceIpPool> selectCoolingExpired(LocalDateTime now) {
        return selectList(Wrappers.<ResourceIpPool>lambdaQuery()
                .eq(ResourceIpPool::getStatus, 5) // cooling
                .le(ResourceIpPool::getCoolingUntil, now));
    }

    /**
     * 占用一个 IP(状态机 available → occupied).
     * 用 update 自带的 WHERE status=1 防并发双卖(没抢到 = 0 行受影响).
     */
    default int markOccupied(String id, String memberUserId, LocalDateTime at) {
        return update(null, Wrappers.<ResourceIpPool>lambdaUpdate()
                .set(ResourceIpPool::getStatus, 2)
                .set(ResourceIpPool::getAssignedMemberId, memberUserId)
                .set(ResourceIpPool::getAssignedAt, at)
                .setSql("assign_count = assign_count + 1")
                .eq(ResourceIpPool::getId, id)
                .eq(ResourceIpPool::getStatus, 1));
    }

    /** 退订: occupied → cooling, 设 cooling 到期时间. */
    default int markCooling(String id, LocalDateTime coolingUntil) {
        return update(null, Wrappers.<ResourceIpPool>lambdaUpdate()
                .set(ResourceIpPool::getStatus, 5)
                .set(ResourceIpPool::getCoolingUntil, coolingUntil)
                .set(ResourceIpPool::getAssignedMemberId, null)
                .set(ResourceIpPool::getAssignedAt, null)
                .eq(ResourceIpPool::getId, id));
    }

    /** 冷却到期 → available. */
    default int markAvailable(String id) {
        return update(null, Wrappers.<ResourceIpPool>lambdaUpdate()
                .set(ResourceIpPool::getStatus, 1)
                .set(ResourceIpPool::getCoolingUntil, null)
                .eq(ResourceIpPool::getId, id));
    }

    /** 列表分页, keyword 模糊匹 ip_address; status / region / ip_type_id 精确过滤. */
    default IPage<ResourceIpPool> selectPageByQuery(IPage<ResourceIpPool> page, ResourceIpPoolPageReqVO reqVO) {
        return selectPage(page, Wrappers.<ResourceIpPool>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), ResourceIpPool::getStatus, reqVO.getStatus())
                .eq(StrUtil.isNotBlank(reqVO.getRegion()), ResourceIpPool::getRegion, reqVO.getRegion())
                .eq(StrUtil.isNotBlank(reqVO.getIpTypeId()), ResourceIpPool::getIpTypeId, reqVO.getIpTypeId())
                .like(StrUtil.isNotBlank(reqVO.getKeyword()), ResourceIpPool::getIpAddress, reqVO.getKeyword())
                .orderByDesc(ResourceIpPool::getCreatedAt));
    }
}
