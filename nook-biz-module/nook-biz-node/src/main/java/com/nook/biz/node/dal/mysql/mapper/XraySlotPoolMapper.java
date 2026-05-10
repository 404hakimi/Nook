package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.slot.XraySlotPoolDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xray 1:1 slot 池 DB 访问层.
 *
 * <p>核心动作:
 * <ul>
 *   <li>{@link #pickFreeSlotForUpdate} + {@link #occupy}: 原子分配空闲 slot, 必须在事务内串行调用</li>
 *   <li>{@link #release}: 释放 slot, 标 used=0 / used_by=NULL</li>
 *   <li>{@link #selectExistingIndexes}: install 后初始化 slot 池前先查已有, 实现幂等</li>
 * </ul>
 *
 * @author nook
 */
@Mapper
public interface XraySlotPoolMapper extends BaseMapper<XraySlotPoolDO> {

    /**
     * 加行锁拿一个空闲 slot (调用方必须在 @Transactional 中调).
     *
     * <p>SELECT FOR UPDATE 锁定后立刻调 {@link #occupy} 标占用, 防并发抢同一 slot.
     *
     * @param serverId 目标 server
     * @return 空闲 slot 的完整行; 没有空闲返回 null
     */
    @Select("""
            SELECT * FROM xray_slot_pool
             WHERE server_id = #{serverId} AND used = 0
             ORDER BY slot_index ASC
             LIMIT 1
             FOR UPDATE
            """)
    XraySlotPoolDO pickFreeSlotForUpdate(@Param("serverId") String serverId);

    /**
     * 占用指定 slot (used=1, used_by=clientId); 与 pickFreeSlotForUpdate 配对在同一事务内调.
     *
     * @param serverId  server
     * @param slotIndex slot 编号
     * @param clientId  占用此 slot 的 xray_client.id
     * @return 影响行数; 应为 1
     */
    @Update("""
            UPDATE xray_slot_pool
               SET used = 1, used_by = #{clientId}, updated_at = NOW()
             WHERE server_id = #{serverId} AND slot_index = #{slotIndex} AND used = 0
            """)
    int occupy(@Param("serverId") String serverId,
               @Param("slotIndex") Integer slotIndex,
               @Param("clientId") String clientId);

    /**
     * 释放指定 slot (used=0, used_by=NULL).
     *
     * @param serverId  server
     * @param slotIndex slot 编号
     * @return 影响行数; 0 = slot 已是空闲 (幂等), 1 = 释放成功
     */
    @Update("""
            UPDATE xray_slot_pool
               SET used = 0, used_by = NULL, updated_at = NOW()
             WHERE server_id = #{serverId} AND slot_index = #{slotIndex}
            """)
    int release(@Param("serverId") String serverId,
                @Param("slotIndex") Integer slotIndex);

    /**
     * 查指定 server 已存在的 slot 编号集合; 用于 install 后初始化 slot 池的幂等检查.
     *
     * @param serverId server
     * @return 已存在的 slot_index 集合
     */
    default Set<Integer> selectExistingIndexes(String serverId) {
        List<XraySlotPoolDO> rows = selectList(Wrappers.<XraySlotPoolDO>lambdaQuery()
                .eq(XraySlotPoolDO::getServerId, serverId)
                .select(XraySlotPoolDO::getSlotIndex));
        return rows.stream().map(XraySlotPoolDO::getSlotIndex).collect(Collectors.toSet());
    }

    /** 列指定 server 的全部 slot 行 (用于运维 / 监控 dashboard). */
    default List<XraySlotPoolDO> selectByServerId(String serverId) {
        return selectList(Wrappers.<XraySlotPoolDO>lambdaQuery()
                .eq(XraySlotPoolDO::getServerId, serverId)
                .orderByAsc(XraySlotPoolDO::getSlotIndex));
    }
}
