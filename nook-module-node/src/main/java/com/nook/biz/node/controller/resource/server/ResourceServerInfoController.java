package com.nook.biz.node.controller.resource.server;

import com.nook.biz.node.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.server.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.server.vo.SystemdStatusRespVO;
import com.nook.biz.node.service.resource.ResourceServerInfoService;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 服务器只读检视
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/server")
@Validated
public class ResourceServerInfoController {

    @Resource
    private ResourceServerInfoService resourceServerInfoService;

    @PostMapping("/{id}/test")
    public Result<ConnectivityTestRespVO> testConnectivity(@PathVariable("id") String id) {
        ConnectivityTestRespVO result = resourceServerInfoService.testConnectivity(id);
        return Result.ok(result);
    }

    @GetMapping("/{id}/system-info")
    public Result<ServerSystemInfoRespVO> getSystemInfo(@PathVariable("id") String id) {
        ServerSystemInfoRespVO info = resourceServerInfoService.getSystemInfo(id);
        return Result.ok(info);
    }

    @GetMapping("/{id}/systemd-status")
    public Result<SystemdStatusRespVO> getSystemdStatus(@PathVariable("id") String id,
                                                        @RequestParam("unit") String unit) {
        SystemdStatusRespVO status = resourceServerInfoService.getSystemdStatus(id, unit);
        return Result.ok(status);
    }

    @GetMapping("/{id}/log")
    public Result<ServiceLogRespVO> getServiceLog(@PathVariable("id") String id,
                                                  @RequestParam("unit") String unit,
                                                  @RequestParam(value = "lines", required = false) Integer lines,
                                                  @RequestParam(value = "level", required = false) String level) {
        ServiceLogRespVO log = resourceServerInfoService.getServiceLog(id, unit, lines, level);
        return Result.ok(log);
    }
}
