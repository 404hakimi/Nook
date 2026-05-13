package com.nook.biz.node.controller.xray;

import com.nook.biz.node.config.WebStreamingProperties;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayServerStatusRespVO;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.server.XrayServerManageService;
import com.nook.common.web.response.Result;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;

/**
 * 管理后台 - Xray 线路服务器运维 (server 上的 xray 实例 status / restart / autostart / install)
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/xray/server")
@Validated
public class XrayServerManageController {

    @Resource
    private XrayServerManageService xrayServerManageService;
    @Resource
    private StreamingEndpointSupport streamingSupport;
    @Resource
    private ResourceServerService resourceServerService;
    @Resource
    private WebStreamingProperties webStreamingProperties;

    @GetMapping("/status")
    public Result<XrayServerStatusRespVO> getXrayStatus(@RequestParam("id") String id) {
        XrayServerStatusRespVO status = xrayServerManageService.getXraySystemdStatus(id);
        return Result.ok(status);
    }

    @PostMapping("/restart")
    public Result<String> restartXray(@RequestParam("id") String id) {
        String out = xrayServerManageService.restart(id);
        return Result.ok(out);
    }

    @PostMapping("/autostart")
    public Result<String> setAutostart(@RequestParam("id") String id,
                                       @RequestParam("enabled") boolean enabled) {
        String out = xrayServerManageService.setAutostart(id, enabled);
        return Result.ok(out);
    }

    @PostMapping(value = "/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installXray(@RequestParam("id") String id,
                                           @Valid @RequestBody XrayServerInstallReqVO reqVO) {
        // Emitter 端比 install 端略宽, 保证 Service 自己 timeout 时还能把错误吐回前端
        int installTimeout = resourceServerService.getServer(id).getInstallTimeoutSeconds();
        Duration emitterTimeout = Duration.ofSeconds(installTimeout).plus(webStreamingProperties.getEmitterBuffer());
        return streamingSupport.stream("install:" + id, emitterTimeout,
                lineSink -> xrayServerManageService.installStreaming(id, reqVO, lineSink));
    }
}
