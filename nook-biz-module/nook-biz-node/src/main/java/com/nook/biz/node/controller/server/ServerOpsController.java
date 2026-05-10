package com.nook.biz.node.controller.server;

import com.nook.biz.node.controller.xray.server.vo.EnableSwapReqVO;
import com.nook.biz.node.service.server.ServerOpsService;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;

/**
 * 服务器通用运维操作: swap / bbr 等独立触发, 跟 xray install 解耦.
 *
 * <p>controller 仅做参数绑定 + 调 service, 校验由 service 内部完成.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/server/{id}/ops")
public class ServerOpsController {

    /** 流式 op 跟 install 量级相当, Emitter 端 = serverOps 时间上限 + 60s. */
    private static final Duration EMITTER_BUFFER = Duration.ofSeconds(60);

    @Resource
    private ServerOpsService serverOpsService;
    @Resource
    private StreamingEndpointSupport streamingSupport;
    @Resource
    private ResourceServerApi resourceServerApi;

    @PostMapping(value = "/swap", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter enableSwap(@PathVariable String id,
                                          @RequestBody @Valid EnableSwapReqVO reqVO) {
        return streamingSupport.stream("ops-swap:" + id, emitterTimeout(id),
                lineSink -> serverOpsService.enableSwap(id, reqVO, lineSink));
    }

    @PostMapping(value = "/bbr", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter enableBbr(@PathVariable String id) {
        return streamingSupport.stream("ops-bbr:" + id, emitterTimeout(id),
                lineSink -> serverOpsService.enableBbr(id, lineSink));
    }

    private Duration emitterTimeout(String id) {
        return Duration.ofSeconds(resourceServerApi.loadCredential(id).getInstallTimeoutSeconds())
                .plus(EMITTER_BUFFER);
    }
}
