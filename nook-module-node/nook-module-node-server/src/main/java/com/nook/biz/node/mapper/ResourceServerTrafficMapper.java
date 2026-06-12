package com.nook.biz.node.mapper;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.entity.ResourceServerTrafficDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 服务器流量计量 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerTrafficMapper extends BaseMapper<ResourceServerTrafficDO> {

    default ResourceServerTrafficDO selectCurrentByServerId(String serverId) {
        return selectOne(Wrappers.<ResourceServerTrafficDO>lambdaQuery()
                .eq(ResourceServerTrafficDO::getServerId, serverId)
                .isNull(ResourceServerTrafficDO::getEndTime));
    }

    default List<ResourceServerTrafficDO> selectCurrentByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return List.of();
        }
        return selectList(Wrappers.<ResourceServerTrafficDO>lambdaQuery()
                .in(ResourceServerTrafficDO::getServerId, serverIds)
                .isNull(ResourceServerTrafficDO::getEndTime));
    }

    default int deleteByServerId(String serverId) {
        return delete(Wrappers.<ResourceServerTrafficDO>lambdaQuery()
                .eq(ResourceServerTrafficDO::getServerId, serverId));
    }
}
