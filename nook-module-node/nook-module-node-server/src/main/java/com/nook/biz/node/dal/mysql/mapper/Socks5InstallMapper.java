package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.Socks5InstallDO;
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
public interface Socks5InstallMapper extends BaseMapper<Socks5InstallDO> {

    /** 按 server_id 查; 不存在返 null. */
    default Socks5InstallDO selectByServerId(String serverId) {
        return selectById(serverId);
    }

    /** 按 server_id 集合批量查 (server_id 即主键). */
    default List<Socks5InstallDO> selectByServerIds(Collection<String> serverIds) {
        return selectBatchIds(serverIds);
    }

    /** 增量更新; updated_at 显式 set 防 Wrapper 跳过 fill. */
    default int updateBySelective(Socks5InstallDO patch) {
        return update(patch, Wrappers.<Socks5InstallDO>lambdaUpdate()
                .set(Socks5InstallDO::getUpdatedAt, LocalDateTime.now())
                .eq(Socks5InstallDO::getServerId, patch.getServerId()));
    }

    /** 指定 server 集合里某 IP 类型的落地子表. */
    default List<Socks5InstallDO> selectByServerIdsAndIpType(Collection<String> serverIds, String ipTypeId) {
        return selectList(Wrappers.<Socks5InstallDO>lambdaQuery()
                .in(Socks5InstallDO::getServerId, serverIds)
                .eq(Socks5InstallDO::getIpTypeId, ipTypeId));
    }

    /** 指定 server 集合里多个 IP 类型的落地子表 (批量算容量用). */
    default List<Socks5InstallDO> selectByServerIdsAndIpTypes(Collection<String> serverIds,
                                                                      Collection<String> ipTypeIds) {
        return selectList(Wrappers.<Socks5InstallDO>lambdaQuery()
                .in(Socks5InstallDO::getServerId, serverIds)
                .in(Socks5InstallDO::getIpTypeId, ipTypeIds));
    }
}
