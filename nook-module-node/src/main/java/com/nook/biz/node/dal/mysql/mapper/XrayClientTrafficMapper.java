package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.client.XrayClientTrafficDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Xray 用户流量累计表 mapper.
 *
 * <p>采样路径只会 upsertDelta (单调递增累加); 不暴露 setXxx 直接覆盖累计值, 避免误差.
 *
 * @author nook
 */
@Mapper
public interface XrayClientTrafficMapper extends BaseMapper<XrayClientTrafficDO> {

    /**
     * 按 client_id 取单条 (一个 client 至多一行, 由 uk_client 保证).
     */
    default XrayClientTrafficDO selectByClientId(String clientId) {
        return selectOne(Wrappers.<XrayClientTrafficDO>lambdaQuery()
                .eq(XrayClientTrafficDO::getClientId, clientId)
                .last("LIMIT 1"));
    }

    /**
     * 批量按 client_id 集合查; sample 后批量读累计 / 列表展示用.
     */
    default List<XrayClientTrafficDO> selectByClientIds(Collection<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) return List.of();
        return selectList(Wrappers.<XrayClientTrafficDO>lambdaQuery()
                .in(XrayClientTrafficDO::getClientId, clientIds));
    }

    /**
     * 单调递增 upsert: 行不存在则 INSERT (累计=delta), 行存在则 uplink+=delta_up / downlink+=delta_down.
     *
     * <p>原子性靠 InnoDB 的 UPDATE; 并发场景下两条 sample 任务都 +delta, 不会丢加.
     * id 由 MP ASSIGN_UUID 在 INSERT 时填充, 这里 ON DUPLICATE 走 UPDATE 分支时 id 自然保留.
     *
     * @return 受影响行数; INSERT 时 1, UPDATE 时 1 (MySQL 文档: ON DUPLICATE 命中 update 计 2 但只有 1 行真改).
     */
    @Update("""
            INSERT INTO xray_client_traffic
                (id, client_id, server_id, uplink_bytes, downlink_bytes, last_sampled_at, created_at, updated_at)
            VALUES
                (#{id}, #{clientId}, #{serverId}, #{deltaUp}, #{deltaDown}, #{sampledAt}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                uplink_bytes    = uplink_bytes   + VALUES(uplink_bytes),
                downlink_bytes  = downlink_bytes + VALUES(downlink_bytes),
                last_sampled_at = VALUES(last_sampled_at),
                server_id       = VALUES(server_id)
            """)
    int upsertDelta(@Param("id") String id,
                    @Param("clientId") String clientId,
                    @Param("serverId") String serverId,
                    @Param("deltaUp") long deltaUp,
                    @Param("deltaDown") long deltaDown,
                    @Param("sampledAt") LocalDateTime sampledAt);

    /**
     * 按 client_id 删累计行; revoke 客户时调用清孤儿;
     * xray_client 是物理删, 这里跟着物理删保持一致.
     */
    default int deleteByClientId(String clientId) {
        return delete(Wrappers.<XrayClientTrafficDO>lambdaQuery()
                .eq(XrayClientTrafficDO::getClientId, clientId));
    }
}
