package com.nook.biz.node.lifecycle;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.mapper.ResourceServerMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 服务器生命周期流转管理
 *
 * @author nook
 */
@Slf4j
@Component
public class ServerLifecycleManager {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ServerLifecycleValidator serverLifecycleValidator;

    /**
     * 服务器生命周期流转: 幂等短路 → 流转表校验 → 按类型前置守卫 → 落新状态
     *
     * @param server   当前服务器
     * @param newState 目标生命周期
     */
    public void transition(ResourceServerDO server, String newState) {
        // 状态未变直接幂等返回
        if (StrUtil.equals(server.getLifecycleState(), newState)) {
            return;
        }
        // 校验流转表
        serverLifecycleValidator.validateTransitionTable(server, newState);
        // 按类型 + 目标态执行上线 / 停用前置守卫
        this.guardByType(server, newState);
        // 落新状态
        resourceServerMapper.updateLifecycleState(server.getId(), newState);
        log.info("[transition] 服务器生命周期切换: id={}, type={}, {} → {}",
                server.getId(), server.getServerType(), server.getLifecycleState(), newState);
    }

    /**
     * 按服务器类型 + 目标态执行上线 / 停用前置守卫
     *
     * @param server   当前服务器
     * @param newState 目标生命周期
     */
    private void guardByType(ResourceServerDO server, String newState) {
        if (ResourceServerTypeEnum.FRONTLINE.matches(server.getServerType())) {
            // 线路机上线: 查 xray 装机 + 域名已绑定
            if (ResourceServerLifecycleEnum.LIVE.matches(newState)) {
                serverLifecycleValidator.validateFrontlineDomainReady(server.getId());
            }
            return;
        }
        // 落地机停用: 查未被客户端占用
        if (ResourceServerLifecycleEnum.RETIRED.matches(newState)) {
            serverLifecycleValidator.validateLandingNotInUse(server.getId());
        }
    }
}
