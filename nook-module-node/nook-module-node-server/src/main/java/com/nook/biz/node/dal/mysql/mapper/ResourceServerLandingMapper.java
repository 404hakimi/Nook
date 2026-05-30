package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 落地机扩展 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerLandingMapper extends BaseMapper<ResourceServerLandingDO> {

    /** 按 server_id 查; 不存在返 null. */
    default ResourceServerLandingDO selectByServerId(String serverId) {
        return selectById(serverId);
    }

    /** 找冷却到期可回 AVAILABLE 的行. */
    default List<ResourceServerLandingDO> selectCoolingExpired(LocalDateTime now) {
        return selectList(Wrappers.<ResourceServerLandingDO>lambdaQuery()
                .eq(ResourceServerLandingDO::getStatus, ResourceServerLandingStatusEnum.COOLING.getState())
                .le(ResourceServerLandingDO::getCoolingUntil, now));
    }

    /** 占用 CAS: AVAILABLE → OCCUPIED; 防并发双卖. */
    default int markOccupied(String serverId, String memberUserId, LocalDateTime at) {
        return update(null, Wrappers.<ResourceServerLandingDO>lambdaUpdate()
                .set(ResourceServerLandingDO::getStatus, ResourceServerLandingStatusEnum.OCCUPIED.getState())
                .set(ResourceServerLandingDO::getOccupiedByMemberId, memberUserId)
                .set(ResourceServerLandingDO::getOccupiedAt, at)
                .set(ResourceServerLandingDO::getUpdatedAt, LocalDateTime.now())
                .setSql("assign_count = assign_count + 1")
                .eq(ResourceServerLandingDO::getServerId, serverId)
                .eq(ResourceServerLandingDO::getStatus, ResourceServerLandingStatusEnum.AVAILABLE.getState()));
    }

    /** 退订: OCCUPIED → COOLING, 写 cooling 到期. */
    default int markCooling(String serverId, LocalDateTime coolingUntil) {
        return update(null, Wrappers.<ResourceServerLandingDO>lambdaUpdate()
                .set(ResourceServerLandingDO::getStatus, ResourceServerLandingStatusEnum.COOLING.getState())
                .set(ResourceServerLandingDO::getCoolingUntil, coolingUntil)
                .set(ResourceServerLandingDO::getOccupiedByMemberId, null)
                .set(ResourceServerLandingDO::getOccupiedAt, null)
                .set(ResourceServerLandingDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerLandingDO::getServerId, serverId));
    }

    /** 冷却到期 → AVAILABLE. */
    default int markAvailable(String serverId) {
        return update(null, Wrappers.<ResourceServerLandingDO>lambdaUpdate()
                .set(ResourceServerLandingDO::getStatus, ResourceServerLandingStatusEnum.AVAILABLE.getState())
                .set(ResourceServerLandingDO::getCoolingUntil, null)
                .set(ResourceServerLandingDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerLandingDO::getServerId, serverId));
    }

    /** 增量更新; updated_at 显式 set 防 Wrapper 跳过 fill. */
    default int updateBySelective(ResourceServerLandingDO patch) {
        return update(patch, Wrappers.<ResourceServerLandingDO>lambdaUpdate()
                .set(ResourceServerLandingDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerLandingDO::getServerId, patch.getServerId()));
    }

    /** 按 ip_type 找一个 AVAILABLE 的行 (assign_count 升序轮换). */
    default ResourceServerLandingDO selectOneAvailable(String ipTypeId) {
        return selectOne(Wrappers.<ResourceServerLandingDO>lambdaQuery()
                .eq(ResourceServerLandingDO::getStatus, ResourceServerLandingStatusEnum.AVAILABLE.getState())
                .eq(StrUtil.isNotBlank(ipTypeId), ResourceServerLandingDO::getIpTypeId, ipTypeId)
                .orderByAsc(ResourceServerLandingDO::getAssignCount)
                .last("LIMIT 1"));
    }

    /** 按 status + ipTypeId 过滤拿候选 server_id (page 前置筛选用; 全空跳过). */
    default List<ResourceServerLandingDO> selectByFilter(String status, String ipTypeId) {
        return selectList(Wrappers.<ResourceServerLandingDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(status), ResourceServerLandingDO::getStatus, status)
                .eq(StrUtil.isNotBlank(ipTypeId), ResourceServerLandingDO::getIpTypeId, ipTypeId));
    }

    /** 指定 server 集合里某 IP 类型的落地子表. */
    default List<ResourceServerLandingDO> selectByServerIdsAndIpType(Collection<String> serverIds, String ipTypeId) {
        return selectList(Wrappers.<ResourceServerLandingDO>lambdaQuery()
                .in(ResourceServerLandingDO::getServerId, serverIds)
                .eq(ResourceServerLandingDO::getIpTypeId, ipTypeId));
    }

    /** 指定 server 集合里多个 IP 类型的落地子表 (批量算容量用). */
    default List<ResourceServerLandingDO> selectByServerIdsAndIpTypes(Collection<String> serverIds,
                                                                      Collection<String> ipTypeIds) {
        return selectList(Wrappers.<ResourceServerLandingDO>lambdaQuery()
                .in(ResourceServerLandingDO::getServerId, serverIds)
                .in(ResourceServerLandingDO::getIpTypeId, ipTypeIds));
    }
}
