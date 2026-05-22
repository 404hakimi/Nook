package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.client.XrayClientTrafficDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Xray 客户端流量累计表访问层.
 *
 * <p>采样路径: 拉 xray 端"当前累计值"入库, SQL 用"当前值 - 上次值"算增量累加;
 * 当前值小于上次值即 xray 进程重启过, 增量按当前值算. SQL 在
 * {@code resources/mapper/XrayClientTrafficMapper.xml}.
 *
 * @author nook
 */
@Mapper
public interface XrayClientTrafficMapper extends BaseMapper<XrayClientTrafficDO> {

    /** 按 client_id 取单条 (uk_client 保证至多一行). */
    default XrayClientTrafficDO selectByClientId(String clientId) {
        return selectOne(Wrappers.<XrayClientTrafficDO>lambdaQuery()
                .eq(XrayClientTrafficDO::getClientId, clientId)
                .last("LIMIT 1"));
    }

    /** 批量按 client_id 集合查; 列表展示用. */
    default List<XrayClientTrafficDO> selectByClientIds(Collection<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) return List.of();
        return selectList(Wrappers.<XrayClientTrafficDO>lambdaQuery()
                .in(XrayClientTrafficDO::getClientId, clientIds));
    }

    /** 按"当前值"批量 upsert; SQL 内算增量再累加, 全部原子. */
    int batchUpsertCounter(@Param("rows") List<TrafficCounterRow> rows);

    /** 批量 upsert 入参; curUp / curDown 是 xray 端的当前累计值, 不是增量. */
    record TrafficCounterRow(
            String id,
            String clientId,
            String serverId,
            long curUp,
            long curDown,
            LocalDateTime sampledAt
    ) {}

    /** 用户主动重置: 累计字段清零 + 把当前值设为新的"上次值"基线. */
    int resetWithBaseline(@Param("id") String id,
                          @Param("clientId") String clientId,
                          @Param("serverId") String serverId,
                          @Param("curUp") long curUp,
                          @Param("curDown") long curDown,
                          @Param("sampledAt") LocalDateTime sampledAt);

    /** 按 client_id 物理删; revoke 客户时清孤儿. */
    default int deleteByClientId(String clientId) {
        return delete(Wrappers.<XrayClientTrafficDO>lambdaQuery()
                .eq(XrayClientTrafficDO::getClientId, clientId));
    }
}
