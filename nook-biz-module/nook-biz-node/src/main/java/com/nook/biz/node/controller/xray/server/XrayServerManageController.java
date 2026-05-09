package com.nook.biz.node.controller.xray.server;

import com.nook.biz.node.controller.support.StreamingEndpointSupport;
import com.nook.biz.node.controller.xray.server.vo.LineServerInstallReqVO;
import com.nook.biz.node.controller.xray.server.vo.ServiceStatusRespVO;
import com.nook.biz.node.service.xray.server.XrayServerManageService;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
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

/** Xray 线路服务器一站式管理: 部署 / 重启 / 状态查询 / 开机自启开关. */
@RestController
@RequestMapping("/admin/node/xray/server")
@RequiredArgsConstructor
@Validated
public class XrayServerManageController {

    /** install 接口整体超时; 部署脚本一般 1-5 分钟, 给 15 分钟兜底 apt 拉包慢的极端情况. */
    private static final Duration INSTALL_EMITTER_TIMEOUT = Duration.ofMinutes(15);

    private final XrayServerManageService xrayServerManageService;
    private final StreamingEndpointSupport streamingSupport;

    /** Xray 服务状态: active / version / 启动时间 / 监听端口 / 开机自启. */
    @GetMapping("/{id}/status")
    public Result<ServiceStatusRespVO> status(@PathVariable @NotBlank String id) {
        return Result.ok(xrayServerManageService.status(id));
    }

    /** 重启 Xray 服务; 客户连接会断 1-2 秒. */
    @PostMapping("/{id}/restart")
    public Result<String> restart(@PathVariable @NotBlank String id) {
        return Result.ok(xrayServerManageService.restart(id));
    }

    /** 开/关 Xray 开机自启 (systemctl enable/disable); 末尾返回 is-enabled 结果给前端确认. */
    @PostMapping("/{id}/autostart")
    public Result<String> autostart(@PathVariable @NotBlank String id,
                                    @RequestParam boolean enabled) {
        return Result.ok(xrayServerManageService.setAutostart(id, enabled));
    }

    /** 一键安装/重装线路服务器; 流式 chunked transfer-encoding, 远端 stdout 每行 flush 一行. */
    @PostMapping(value = "/{id}/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter install(@PathVariable @NotBlank String id,
                                       @RequestBody @Valid LineServerInstallReqVO reqVO) {
        return streamingSupport.stream("install:" + id, INSTALL_EMITTER_TIMEOUT,
                lineSink -> xrayServerManageService.installStreaming(id, reqVO, lineSink));
    }
}
