package com.nook.biz.node.controller.xray.server;

import com.nook.biz.node.controller.xray.server.vo.LineServerInstallReqVO;
import com.nook.biz.node.controller.xray.server.vo.ServiceStatusRespVO;
import com.nook.biz.node.resource.service.ResourceServerService;
import com.nook.biz.node.service.xray.server.XrayServerManageService;
import com.nook.common.web.response.Result;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
 * Xray 线路服务器管理接口; controller 仅做参数绑定 + 调 service, 校验由 service 内部完成.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/xray/server")
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
    public Result<ServiceStatusRespVO> status(@PathVariable String id) {
        return Result.ok(xrayServerManageService.getXraySystemdStatus(id));
    }

    @PostMapping("/{id}/restart")
    public Result<String> restart(@PathVariable String id) {
        return Result.ok(xrayServerManageService.restart(id));
    }

    @PostMapping("/{id}/autostart")
    public Result<String> autostart(@PathVariable String id,
                                    @RequestParam boolean enabled) {
        return Result.ok(xrayServerManageService.setAutostart(id, enabled));
    }

    @PostMapping(value = "/{id}/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter install(@PathVariable String id,
                                       @RequestBody @Valid LineServerInstallReqVO reqVO) {
        // Emitter 端比 install 端略宽 (+60s), 保证 Service 自己 timeout 时还能把错误吐回前端
        Duration emitterTimeout = Duration.ofSeconds(resourceServerService.findById(id).getInstallTimeoutSeconds()).plus(EMITTER_BUFFER);
        return streamingSupport.stream("install:" + id, emitterTimeout,
                lineSink -> xrayServerManageService.installStreaming(id, reqVO, lineSink));
    }
}
