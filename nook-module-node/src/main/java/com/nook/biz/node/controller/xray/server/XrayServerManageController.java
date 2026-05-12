package com.nook.biz.node.controller.xray.server;

import com.nook.biz.node.controller.xray.server.vo.LineServerInstallReqVO;
import com.nook.biz.node.controller.xray.server.vo.ServiceStatusRespVO;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.server.XrayServerManageService;
import com.nook.common.web.response.Result;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;

/**
 * 管理后台 - Xray 线路服务器
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/xray/server")
@Validated
public class XrayServerManageController {

    /** Emitter 超时 = installTimeoutSeconds + buffer, 留给 service 主动报错 / 收尾的余地. */
    private static final Duration EMITTER_BUFFER = Duration.ofSeconds(60);

    @Resource
    private XrayServerManageService xrayServerManageService;
    @Resource
    private StreamingEndpointSupport streamingSupport;
    @Resource
    private ResourceServerService resourceServerService;

    @GetMapping("/{id}/status")
    public Result<ServiceStatusRespVO> getXrayStatus(@PathVariable("id") String id) {
        ServiceStatusRespVO status = xrayServerManageService.getXraySystemdStatus(id);
        return Result.ok(status);
    }

    @PostMapping("/{id}/restart")
    public Result<String> restartXray(@PathVariable("id") String id) {
        String out = xrayServerManageService.restart(id);
        return Result.ok(out);
    }

    @PostMapping("/{id}/autostart")
    public Result<String> setAutostart(@PathVariable("id") String id,
                                       @RequestParam("enabled") boolean enabled) {
        String out = xrayServerManageService.setAutostart(id, enabled);
        return Result.ok(out);
    }

    @PostMapping(value = "/{id}/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installXray(@PathVariable("id") String id,
                                           @RequestBody @Valid LineServerInstallReqVO reqVO) {
        // Emitter 端比 install 端略宽 (+60s), 保证 Service 自己 timeout 时还能把错误吐回前端
        int installTimeout = resourceServerService.getServer(id).getInstallTimeoutSeconds();
        Duration emitterTimeout = Duration.ofSeconds(installTimeout).plus(EMITTER_BUFFER);
        return streamingSupport.stream("install:" + id, emitterTimeout,
                lineSink -> xrayServerManageService.installStreaming(id, reqVO, lineSink));
    }
}
