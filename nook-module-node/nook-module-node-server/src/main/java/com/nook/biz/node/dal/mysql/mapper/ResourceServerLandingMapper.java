package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

    /** 按 server_id 集合批量查 (server_id 即主键). */
    default List<ResourceServerLandingDO> selectByServerIds(Collection<String> serverIds) {
        return selectBatchIds(serverIds);
    }

    /** 增量更新; updated_at 显式 set 防 Wrapper 跳过 fill. */
    default int updateBySelective(ResourceServerLandingDO patch) {
        return update(patch, Wrappers.<ResourceServerLandingDO>lambdaUpdate()
                .set(ResourceServerLandingDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerLandingDO::getServerId, patch.getServerId()));
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
