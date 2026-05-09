package com.nook.biz.node.controller.server;

import com.nook.biz.node.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.server.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.server.vo.SystemdStatusRespVO;
import com.nook.biz.node.service.server.ServerInspectorService;
import com.nook.common.web.response.Result;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 服务器只读检视: 主机可达性 / 系统信息 / 任意 systemd 服务状态 / 任意 unit 日志. */
@RestController
@RequestMapping("/admin/node/server")
@RequiredArgsConstructor
@Validated
public class ServerInspectorController {

    private final ServerInspectorService serverInspectorService;

    /** 主机可达性探活 (SSH 跑 'true'); 失败已包成 success=false 结构化结果, 不抛 5xx. */
    @PostMapping("/{id}/test")
    public Result<ConnectivityTestRespVO> testConnectivity(@PathVariable @NotBlank String id) {
        return Result.ok(serverInspectorService.testConnectivity(id));
    }

    /** 操作系统级别基本信息 (hostname / 内存 / 磁盘 等), 不依赖 Xray 是否在跑. */
    @GetMapping("/{id}/system-info")
    public Result<ServerSystemInfoRespVO> systemInfo(@PathVariable @NotBlank String id) {
        return Result.ok(serverInspectorService.getSystemInfo(id));
    }

    /** 指定 systemd unit 的通用状态 (active / 启动时间 / 开机自启); unit 必填 (xray / sshd / nginx 等). */
    @GetMapping("/{id}/systemd-status")
    public Result<SystemdStatusRespVO> systemdStatus(@PathVariable @NotBlank String id,
                                                     @RequestParam @NotBlank String unit) {
        return Result.ok(serverInspectorService.getSystemdStatus(id, unit));
    }

    /** 指定 systemd unit 的 journalctl 日志; unit 必填 (如 xray / sshd / nginx), lines 默认 100 上限 5000, level 取 all/warning/err. */
    @GetMapping("/{id}/log")
    public Result<ServiceLogRespVO> log(@PathVariable @NotBlank String id,
                                        @RequestParam @NotBlank String unit,
                                        @RequestParam(required = false) Integer lines,
                                        @RequestParam(required = false) String level) {
        return Result.ok(serverInspectorService.getLog(id, unit, lines, level));
    }
}
