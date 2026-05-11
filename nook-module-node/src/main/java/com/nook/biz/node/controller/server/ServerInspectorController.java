package com.nook.biz.node.controller.server;

import com.nook.biz.node.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.server.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.server.vo.SystemdStatusRespVO;
import com.nook.biz.node.service.server.ServerInspectorService;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务器只读检视接口; controller 仅做参数绑定 + 调 service, 校验在 service 层做.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/server")
public class ServerInspectorController {

    @Resource
    private ServerInspectorService serverInspectorService;

    @PostMapping("/{id}/test")
    public Result<ConnectivityTestRespVO> testConnectivity(@PathVariable String id) {
        return Result.ok(serverInspectorService.testConnectivity(id));
    }

    @GetMapping("/{id}/system-info")
    public Result<ServerSystemInfoRespVO> systemInfo(@PathVariable String id) {
        return Result.ok(serverInspectorService.getSystemInfo(id));
    }

    @GetMapping("/{id}/systemd-status")
    public Result<SystemdStatusRespVO> systemdStatus(@PathVariable String id,
                                                     @RequestParam String unit) {
        return Result.ok(serverInspectorService.getSystemdStatus(id, unit));
    }

    @GetMapping("/{id}/log")
    public Result<ServiceLogRespVO> log(@PathVariable String id,
                                        @RequestParam String unit,
                                        @RequestParam(required = false) Integer lines,
                                        @RequestParam(required = false) String level) {
        return Result.ok(serverInspectorService.getLog(id, unit, lines, level));
    }
}
