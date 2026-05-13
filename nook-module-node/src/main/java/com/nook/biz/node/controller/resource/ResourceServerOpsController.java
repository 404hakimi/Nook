package com.nook.biz.node.controller.resource;

import com.nook.biz.node.config.WebStreamingProperties;
import com.nook.biz.node.controller.resource.vo.EnableSwapReqVO;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.resource.ResourceServerOpsService;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
 * 管理后台 - 服务器通用运维操作; swap / bbr 等独立触发, 跟 xray install 解耦
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server")
@Validated
public class ResourceServerOpsController {

    @Resource
    private ResourceServerOpsService resourceServerOpsService;
    @Resource
    private StreamingEndpointSupport streamingSupport;
    @Resource
    private ResourceServerService resourceServerService;
    @Resource
    private WebStreamingProperties webStreamingProperties;

    @PostMapping(value = "/enable-swap", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter enableSwap(@RequestParam("id") String id,
                                          @Valid @RequestBody EnableSwapReqVO reqVO) {
        return streamingSupport.stream("ops-swap:" + id, emitterTimeout(id),
                lineSink -> resourceServerOpsService.enableSwap(id, reqVO, lineSink));
    }

    @PostMapping(value = "/enable-bbr", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter enableBbr(@RequestParam("id") String id) {
        return streamingSupport.stream("ops-bbr:" + id, emitterTimeout(id),
                lineSink -> resourceServerOpsService.enableBbr(id, lineSink));
    }

    private Duration emitterTimeout(String id) {
        int installTimeout = resourceServerService.getServer(id).getInstallTimeoutSeconds();
        return Duration.ofSeconds(installTimeout).plus(webStreamingProperties.getEmitterBuffer());
    }
}
