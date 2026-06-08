package com.nook.biz.node.convert.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.controller.resource.vo.frontline.ResourceServerFrontlineRespVO;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 线路机扩展 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceServerFrontlineConvert {

    ResourceServerFrontlineConvert INSTANCE = Mappers.getMapper(ResourceServerFrontlineConvert.class);

    ResourceServerFrontlineRespVO convert(ResourceServerFrontlineDO bean);

    /** 回填 agent 在线态: 据最近心跳算 elapsedSec + onlineState (连表 SQL 只出原始字段). */
    static void fillOnlineState(ServerFrontlineListItemRespVO vo, LocalDateTime now) {
        Long elapsedSec = ObjectUtil.isNull(vo.getLastHeartbeatAt()) ? null
                : Duration.between(vo.getLastHeartbeatAt(), now).getSeconds();
        vo.setElapsedSec(elapsedSec);
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec).name());
    }
}
