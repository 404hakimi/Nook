package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.slot.XraySlotPoolDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xray 1:1 slot 池访问层.
 *
 * <p>分配流程: {@link #pickFreeSlotForUpdate} (行锁拿空闲) → {@link #occupy} (标占用),
 * 两步必须同一事务. SQL 在 {@code resources/mapper/XraySlotPoolMapper.xml}.
 *
 * @author nook
 */
@Mapper
public interface XraySlotPoolMapper extends BaseMapper<XraySlotPoolDO> {

    /** SELECT FOR UPDATE 加行锁拿一个空闲 slot; 调用方必须在 @Transactional 中. */
    XraySlotPoolDO pickFreeSlotForUpdate(@Param("serverId") String serverId);

    /** 占用指定 slot, 与 {@link #pickFreeSlotForUpdate} 配对; 影响行数应为 1. */
    int occupy(@Param("serverId") String serverId,
               @Param("slotIndex") Integer slotIndex,
               @Param("clientId") String clientId);

    /** 释放指定 slot; 影响行数 0 = 已是空闲 (幂等), 1 = 释放成功. */
    int release(@Param("serverId") String serverId,
                @Param("slotIndex") Integer slotIndex);

    /** 查指定 server 已存在的 slot 编号集合; install 后初始化 slot 池前的幂等检查用. */
    default Set<Integer> selectExistingIndexes(String serverId) {
        List<XraySlotPoolDO> rows = selectList(Wrappers.<XraySlotPoolDO>lambdaQuery()
                .eq(XraySlotPoolDO::getServerId, serverId)
                .select(XraySlotPoolDO::getSlotIndex));
        return rows.stream().map(XraySlotPoolDO::getSlotIndex).collect(Collectors.toSet());
    }

    /** 列指定 server 全部 slot 行 (运维 / 监控用). */
    default List<XraySlotPoolDO> selectByServerId(String serverId) {
        return selectList(Wrappers.<XraySlotPoolDO>lambdaQuery()
                .eq(XraySlotPoolDO::getServerId, serverId)
                .orderByAsc(XraySlotPoolDO::getSlotIndex));
    }
}
