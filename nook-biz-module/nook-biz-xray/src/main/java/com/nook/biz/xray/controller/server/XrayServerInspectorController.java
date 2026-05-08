package com.nook.biz.xray.controller.server;

import com.nook.biz.xray.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.xray.controller.server.vo.XrayLogRespVO;
import com.nook.biz.xray.controller.server.vo.XrayServiceStatusRespVO;
import com.nook.biz.xray.service.XrayServerInspector;
import com.nook.common.web.response.Result;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中转线路服务器只读检视接口 — 系统信息 / Xray 服务状态 / Xray 日志, 三块各一个端点;
 * 与 {@link XrayServerOpsController} 区分: 那里是"写"操作 (探活 / 重启 / 安装), 这里仅"读"。
 */
@RestController
@RequestMapping("/admin/xray/servers")
@RequiredArgsConstructor
@Validated
public class XrayServerInspectorController {

    private final XrayServerInspector xrayServerInspector;

    /** 操作系统级别基本信息 (hostname / 内存 / 磁盘 等), 不依赖 Xray 是否在跑。 */
    @GetMapping("/{id}/system-info")
    public Result<ServerSystemInfoRespVO> systemInfo(@PathVariable @NotBlank String id) {
        return Result.ok(xrayServerInspector.getSystemInfo(id));
    }

    /** Xray systemd 服务状态 (active / version / 启动时间 / 监听端口); 不含日志。 */
    @GetMapping("/{id}/service-status")
    public Result<XrayServiceStatusRespVO> serviceStatus(@PathVariable @NotBlank String id) {
        return Result.ok(xrayServerInspector.getServiceStatus(id));
    }

    /**
     * Xray journalctl 日志。
     * @param lines 默认 100, 上限 5000
     * @param level all (默认) / warning / err
     */
    @GetMapping("/{id}/log")
    public Result<XrayLogRespVO> log(@PathVariable @NotBlank String id,
                                     @RequestParam(required = false) Integer lines,
                                     @RequestParam(required = false) String level) {
        return Result.ok(xrayServerInspector.getLog(id, lines, level));
    }
}
