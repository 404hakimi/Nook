package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nook.biz.node.dal.dataobject.agent.AgentRuntimeConfigDO;
import org.apache.ibatis.annotations.Mapper;

/** Agent 运行时配置 (单表). */
@Mapper
public interface AgentRuntimeConfigMapper extends BaseMapper<AgentRuntimeConfigDO> {
}
