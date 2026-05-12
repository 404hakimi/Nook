package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
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
public interface ResourceIpPoolMapper extends BaseMapper<ResourceIpPoolDO> {

    /**
     * 按 region + ip_type_id 找一个可分配的 IP.
     * 优先 assign_count 低(尽量轮换避免某 IP 一直被同人用).
     * @return null 表示池子里没货
     */
    default ResourceIpPoolDO selectAvailable(String region, String ipTypeId) {
        return selectOne(Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ResourceIpPoolDO::getStatus, 1) // available
                .eq(StrUtil.isNotBlank(region), ResourceIpPoolDO::getRegion, region)
                .eq(StrUtil.isNotBlank(ipTypeId), ResourceIpPoolDO::getIpTypeId, ipTypeId)
                .orderByAsc(ResourceIpPoolDO::getAssignCount)
                .last("LIMIT 1"));
    }

    /** 按 ip_address 唯一查; 录入时查重用. */
    default ResourceIpPoolDO selectByIpAddress(String ipAddress) {
        return selectOne(Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ResourceIpPoolDO::getIpAddress, ipAddress)
                .last("LIMIT 1"));
    }

    /** ip_address + 排除指定 id 是否重复(更新时查重). */
    default boolean existsByIpAddressExcludingId(String ipAddress, String excludeId) {
        return exists(Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ResourceIpPoolDO::getIpAddress, ipAddress)
                .ne(ResourceIpPoolDO::getId, excludeId));
    }

    /** 找冷却期已到、可以回到 available 的 IP. */
    default List<ResourceIpPoolDO> selectCoolingExpired(LocalDateTime now) {
        return selectList(Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ResourceIpPoolDO::getStatus, 5) // cooling
                .le(ResourceIpPoolDO::getCoolingUntil, now));
    }

    /**
     * 占用一个 IP(状态机 available → occupied).
     * 用 update 自带的 WHERE status=1 防并发双卖(没抢到 = 0 行受影响).
     */
    default int markOccupied(String id, String memberUserId, LocalDateTime at) {
        return update(null, Wrappers.<ResourceIpPoolDO>lambdaUpdate()
                .set(ResourceIpPoolDO::getStatus, 2)
                .set(ResourceIpPoolDO::getAssignedMemberId, memberUserId)
                .set(ResourceIpPoolDO::getAssignedAt, at)
                .setSql("assign_count = assign_count + 1")
                .eq(ResourceIpPoolDO::getId, id)
                .eq(ResourceIpPoolDO::getStatus, 1));
    }

    /** 退订: occupied → cooling, 设 cooling 到期时间. */
    default int markCooling(String id, LocalDateTime coolingUntil) {
        return update(null, Wrappers.<ResourceIpPoolDO>lambdaUpdate()
                .set(ResourceIpPoolDO::getStatus, 5)
                .set(ResourceIpPoolDO::getCoolingUntil, coolingUntil)
                .set(ResourceIpPoolDO::getAssignedMemberId, null)
                .set(ResourceIpPoolDO::getAssignedAt, null)
                .eq(ResourceIpPoolDO::getId, id));
    }

    /** 冷却到期 → available. */
    default int markAvailable(String id) {
        return update(null, Wrappers.<ResourceIpPoolDO>lambdaUpdate()
                .set(ResourceIpPoolDO::getStatus, 1)
                .set(ResourceIpPoolDO::getCoolingUntil, null)
                .eq(ResourceIpPoolDO::getId, id));
    }

    /** 列表分页, keyword 模糊匹 ip_address; status / region / ip_type_id 精确过滤. */
    default IPage<ResourceIpPoolDO> selectPageByQuery(IPage<ResourceIpPoolDO> page, ResourceIpPoolPageReqVO reqVO) {
        return selectPage(page, Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), ResourceIpPoolDO::getStatus, reqVO.getStatus())
                .eq(StrUtil.isNotBlank(reqVO.getRegion()), ResourceIpPoolDO::getRegion, reqVO.getRegion())
                .eq(StrUtil.isNotBlank(reqVO.getIpTypeId()), ResourceIpPoolDO::getIpTypeId, reqVO.getIpTypeId())
                .like(StrUtil.isNotBlank(reqVO.getKeyword()), ResourceIpPoolDO::getIpAddress, reqVO.getKeyword())
                .orderByDesc(ResourceIpPoolDO::getCreatedAt));
    }
}
