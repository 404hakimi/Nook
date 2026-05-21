package com.nook.biz.node.convert.agent;

import com.nook.biz.node.controller.agent.admin.vo.AdminAgentTaskRespVO;
import com.nook.biz.node.dal.dataobject.agent.AgentTaskDO;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** AgentTaskDO ↔ AdminAgentTaskRespVO. */
@Mapper
public interface AgentTaskConvert {

    AgentTaskConvert INSTANCE = Mappers.getMapper(AgentTaskConvert.class);

    AdminAgentTaskRespVO convert(AgentTaskDO entity);

    List<AdminAgentTaskRespVO> convertList(List<AgentTaskDO> list);

    default PageResult<AdminAgentTaskRespVO> convertPage(PageResult<AgentTaskDO> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }
}
