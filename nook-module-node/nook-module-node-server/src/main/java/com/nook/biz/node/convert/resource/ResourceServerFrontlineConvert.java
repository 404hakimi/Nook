package com.nook.biz.node.convert.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 线路机列表项 Convert (仅在线态回填; 域名见 xray_install)
 *
 * @author nook
 */
public final class ResourceServerFrontlineConvert {

    private ResourceServerFrontlineConvert() {
    }

    /** 回填 agent 在线态: 据最近心跳算 elapsedSec + onlineState (连表 SQL 只出原始字段). */
    public static void fillOnlineState(ServerFrontlineListItemRespVO vo, LocalDateTime now) {
        Long elapsedSec = ObjectUtil.isNull(vo.getLastHeartbeatAt()) ? null
                : Duration.between(vo.getLastHeartbeatAt(), now).getSeconds();
        vo.setElapsedSec(elapsedSec);
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec).name());
    }
}
