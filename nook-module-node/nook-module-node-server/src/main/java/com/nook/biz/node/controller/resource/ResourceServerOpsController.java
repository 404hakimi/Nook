package com.nook.biz.node.controller.resource;

import com.nook.framework.web.WebStreamingProperties;
import com.nook.biz.node.controller.resource.vo.EnableSwapReqVO;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerOpsService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;

/**
 * 管理后台 - 服务器通用运维 Controller (swap / bbr 等独立触发, 跟 xray install 解耦)
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server")
@Validated
@RequiredArgsConstructor
public class ResourceServerOpsController {

    private final ResourceServerOpsService resourceServerOpsService;
    private final StreamingEndpointSupport streamingSupport;
    private final ResourceServerValidator serverValidator;
    private final ResourceServerCredentialService credentialService;
    private final WebStreamingProperties webStreamingProperties;

    /**
     * 启用 swap 分区 (流式) ; 跟 xray install 同 emitter 模式
     *
     * @param id    server 编号
     * @param reqVO swap 入参
     * @return 流式响应
     */
    @PostMapping(value = "/enable-swap", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter enableSwap(@RequestParam("id") String id,
                                          @Valid @RequestBody EnableSwapReqVO reqVO) {
        return streamingSupport.stream("ops-swap:" + id, emitterTimeout(id),
                lineSink -> resourceServerOpsService.enableSwap(id, reqVO, lineSink));
    }

    /**
     * 启用 BBR 拥塞控制 (流式)
     *
     * @param id server 编号
     * @return 流式响应
     */
    @PostMapping(value = "/enable-bbr", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter enableBbr(@RequestParam("id") String id) {
        return streamingSupport.stream("ops-bbr:" + id, emitterTimeout(id),
                lineSink -> resourceServerOpsService.enableBbr(id, lineSink));
    }

    private Duration emitterTimeout(String id) {
        serverValidator.validateExists(id);
        int installTimeout = credentialService.requireByServerId(id).getInstallTimeoutSeconds();
        return Duration.ofSeconds(installTimeout).plus(webStreamingProperties.getEmitterBuffer());
    }
}
