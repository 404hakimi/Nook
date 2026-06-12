package com.nook.biz.node.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.entity.Socks5InstallDO;
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

    default Socks5InstallDO selectByServerId(String serverId) {
        return selectById(serverId);
    }

    default List<Socks5InstallDO> selectByServerIds(Collection<String> serverIds) {
        return selectBatchIds(serverIds);
    }

    default int updateBySelective(Socks5InstallDO patch) {
        return update(patch, Wrappers.<Socks5InstallDO>lambdaUpdate()
                .set(Socks5InstallDO::getUpdatedAt, LocalDateTime.now())
                .eq(Socks5InstallDO::getServerId, patch.getServerId()));
    }

    default List<Socks5InstallDO> selectByServerIdsAndIpType(Collection<String> serverIds, String ipTypeId) {
        return selectList(Wrappers.<Socks5InstallDO>lambdaQuery()
                .in(Socks5InstallDO::getServerId, serverIds)
                .eq(Socks5InstallDO::getIpTypeId, ipTypeId));
    }

    default List<Socks5InstallDO> selectByServerIdsAndIpTypes(Collection<String> serverIds,
                                                                      Collection<String> ipTypeIds) {
        return selectList(Wrappers.<Socks5InstallDO>lambdaQuery()
                .in(Socks5InstallDO::getServerId, serverIds)
                .in(Socks5InstallDO::getIpTypeId, ipTypeIds));
    }
}
