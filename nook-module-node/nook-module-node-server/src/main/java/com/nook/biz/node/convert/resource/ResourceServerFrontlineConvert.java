package com.nook.biz.node.convert.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;

import java.time.Duration;
import java.time.LocalDateTime;

public final class ResourceServerFrontlineConvert {

    private ResourceServerFrontlineConvert() {
    }

    public static void fillOnlineState(ServerFrontlineListItemRespVO vo, LocalDateTime now) {
        Long elapsedSec = ObjectUtil.isNull(vo.getLastHeartbeatAt()) ? null
                : Duration.between(vo.getLastHeartbeatAt(), now).getSeconds();
        vo.setElapsedSec(elapsedSec);
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec).name());
    }
}
