package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.api.enums.ResourceIpPoolLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceIpPoolStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IP 池 Mapper. 状态机相关:
 *   - 兑换流挑 IP → selectAvailable(region, ipTypeId): 仅 lifecycle=LIVE AND status=AVAILABLE
 *   - 退订冷却期到了 → selectCoolingExpired(now) 然后批量回 AVAILABLE
 *   - 占用/退订/恢复 走 CAS 状态机, 不直接 UPDATE 字段
 *
 * @author nook
 */
@Mapper
public interface ResourceIpPoolMapper extends BaseMapper<ResourceIpPoolDO> {

    /**
     * 按 region + ip_type_id 找一个可分配的 IP.
     * 硬约束: lifecycle=LIVE AND status=AVAILABLE.
     * 按 created_at 升序取最旧的可用 IP, 让池里行轮转使用避免长期闲置.
     *
     * @return null 表示池子里没货
     */
    default ResourceIpPoolDO selectAvailable(String region, String ipTypeId) {
        return selectOne(Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ResourceIpPoolDO::getLifecycleState, ResourceIpPoolLifecycleEnum.LIVE.getState())
                .eq(ResourceIpPoolDO::getStatus, ResourceIpPoolStatusEnum.AVAILABLE.getState())
                .eq(StrUtil.isNotBlank(region), ResourceIpPoolDO::getRegion, region)
                .eq(StrUtil.isNotBlank(ipTypeId), ResourceIpPoolDO::getIpTypeId, ipTypeId)
                .orderByAsc(ResourceIpPoolDO::getCreatedAt)
                .last("LIMIT 1"));
    }

    /** 按 agent_token 查 IP 池行 (landing agent push 接口鉴权用); 找不到返 null. */
    default ResourceIpPoolDO selectByAgentToken(String agentToken) {
        return selectOne(Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ResourceIpPoolDO::getAgentToken, agentToken)
                .last("LIMIT 1"));
    }

    /** 按 ip_address 唯一查; 录入时查重用. */
    default ResourceIpPoolDO selectByIpAddress(String ipAddress) {
        return selectOne(Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ResourceIpPoolDO::getIpAddress, ipAddress)
                .last("LIMIT 1"));
    }

    /** ip_address + 排除指定 id 是否重复 (更新时查重). */
    default boolean existsByIpAddressExcludingId(String ipAddress, String excludeId) {
        return exists(Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ResourceIpPoolDO::getIpAddress, ipAddress)
                .ne(ResourceIpPoolDO::getId, excludeId));
    }

    /** 找冷却期已到、可以回到 AVAILABLE 的 IP. */
    default List<ResourceIpPoolDO> selectCoolingExpired(LocalDateTime now) {
        return selectList(Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(ResourceIpPoolDO::getStatus, ResourceIpPoolStatusEnum.COOLING.getState())
                .le(ResourceIpPoolDO::getCoolingUntil, now));
    }

    /**
     * 占用 IP (CAS): AVAILABLE → OCCUPIED.
     * 用 update 自带的 WHERE status=AVAILABLE 防并发双卖 (没抢到 = 0 行受影响).
     */
    default int markOccupied(String id, String memberUserId, LocalDateTime at) {
        return update(null, Wrappers.<ResourceIpPoolDO>lambdaUpdate()
                .set(ResourceIpPoolDO::getStatus, ResourceIpPoolStatusEnum.OCCUPIED.getState())
                .set(ResourceIpPoolDO::getOccupiedByMemberId, memberUserId)
                .set(ResourceIpPoolDO::getOccupiedAt, at)
                .set(ResourceIpPoolDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolDO::getId, id)
                .eq(ResourceIpPoolDO::getStatus, ResourceIpPoolStatusEnum.AVAILABLE.getState()));
    }

    /** 退订: OCCUPIED → COOLING, 设 cooling 到期时间. */
    default int markCooling(String id, LocalDateTime coolingUntil) {
        return update(null, Wrappers.<ResourceIpPoolDO>lambdaUpdate()
                .set(ResourceIpPoolDO::getStatus, ResourceIpPoolStatusEnum.COOLING.getState())
                .set(ResourceIpPoolDO::getCoolingUntil, coolingUntil)
                .set(ResourceIpPoolDO::getOccupiedByMemberId, null)
                .set(ResourceIpPoolDO::getOccupiedAt, null)
                .set(ResourceIpPoolDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolDO::getId, id));
    }

    /** 冷却到期 → AVAILABLE. */
    default int markAvailable(String id) {
        return update(null, Wrappers.<ResourceIpPoolDO>lambdaUpdate()
                .set(ResourceIpPoolDO::getStatus, ResourceIpPoolStatusEnum.AVAILABLE.getState())
                .set(ResourceIpPoolDO::getCoolingUntil, null)
                .set(ResourceIpPoolDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolDO::getId, id));
    }

    /** 切换 lifecycle_state (admin 上线 / 退役). */
    default int updateLifecycleState(String id, String newState) {
        return update(null, Wrappers.<ResourceIpPoolDO>lambdaUpdate()
                .set(ResourceIpPoolDO::getLifecycleState, newState)
                .set(ResourceIpPoolDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolDO::getId, id));
    }

    /** 列表分页, keyword 模糊匹 ip_address; lifecycle/status/region/ipType 精确过滤. */
    default IPage<ResourceIpPoolDO> selectPageByQuery(IPage<ResourceIpPoolDO> page, ResourceIpPoolPageReqVO reqVO) {
        return selectPage(page, Wrappers.<ResourceIpPoolDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(reqVO.getLifecycleState()), ResourceIpPoolDO::getLifecycleState, reqVO.getLifecycleState())
                .eq(StrUtil.isNotBlank(reqVO.getStatus()), ResourceIpPoolDO::getStatus, reqVO.getStatus())
                .eq(StrUtil.isNotBlank(reqVO.getRegion()), ResourceIpPoolDO::getRegion, reqVO.getRegion())
                .eq(StrUtil.isNotBlank(reqVO.getIpTypeId()), ResourceIpPoolDO::getIpTypeId, reqVO.getIpTypeId())
                .like(StrUtil.isNotBlank(reqVO.getKeyword()), ResourceIpPoolDO::getIpAddress, reqVO.getKeyword())
                .orderByDesc(ResourceIpPoolDO::getCreatedAt));
    }
}
