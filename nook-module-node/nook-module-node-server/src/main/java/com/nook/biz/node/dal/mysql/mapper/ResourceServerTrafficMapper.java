package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 服务器流量周期归档 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerTrafficMapper extends BaseMapper<ResourceServerTrafficDO> {

    /** 某机历史周期(按起点倒序). */
    default List<ResourceServerTrafficDO> selectByServer(String serverId) {
        return selectList(Wrappers.<ResourceServerTrafficDO>lambdaQuery()
                .eq(ResourceServerTrafficDO::getServerId, serverId)
                .orderByDesc(ResourceServerTrafficDO::getPeriodStart));
    }
}
