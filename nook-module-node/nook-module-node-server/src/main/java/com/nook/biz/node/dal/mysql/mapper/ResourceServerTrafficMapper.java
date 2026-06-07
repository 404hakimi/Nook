package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;
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

    /** 某机当周期行(end_time 空); 无返 null. */
    default ResourceServerTrafficDO selectCurrentByServerId(String serverId) {
        return selectOne(Wrappers.<ResourceServerTrafficDO>lambdaQuery()
                .eq(ResourceServerTrafficDO::getServerId, serverId)
                .isNull(ResourceServerTrafficDO::getEndTime));
    }

    /** 一批服务器的当周期行(end_time 空). */
    default List<ResourceServerTrafficDO> selectCurrentByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return List.of();
        }
        return selectList(Wrappers.<ResourceServerTrafficDO>lambdaQuery()
                .in(ResourceServerTrafficDO::getServerId, serverIds)
                .isNull(ResourceServerTrafficDO::getEndTime));
    }
}
