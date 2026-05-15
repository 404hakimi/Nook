package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.SystemdStatusRespVO;
import com.nook.biz.node.service.resource.ResourceServerInfoService;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 服务器只读检视; 跟 ResourceServerController 共用 /admin/resource/server 前缀
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server")
@Validated
public class ResourceServerInfoController {

    @Resource
    private ResourceServerInfoService resourceServerInfoService;

    @PostMapping("/connectivity-test")
    public Result<ConnectivityTestRespVO> testConnectivity(@RequestParam("id") String id) {
        ConnectivityTestRespVO result = resourceServerInfoService.testConnectivity(id);
        return Result.ok(result);
    }

    @GetMapping("/system-info")
    public Result<ServerSystemInfoRespVO> getSystemInfo(@RequestParam("id") String id) {
        ServerSystemInfoRespVO info = resourceServerInfoService.getSystemInfo(id);
        return Result.ok(info);
    }

    @GetMapping("/systemd-status")
    public Result<SystemdStatusRespVO> getSystemdStatus(@RequestParam("id") String id,
                                                        @RequestParam("unit") String unit) {
        SystemdStatusRespVO status = resourceServerInfoService.getSystemdStatus(id, unit);
        return Result.ok(status);
    }

    @GetMapping("/service-log")
    public Result<ServiceLogRespVO> getServiceLog(@RequestParam("id") String id,
                                                  @RequestParam("unit") String unit,
                                                  @RequestParam(value = "lines", required = false) Integer lines,
                                                  @RequestParam(value = "level", required = false) String level,
                                                  @RequestParam(value = "keyword", required = false) String keyword) {
        ServiceLogRespVO log = resourceServerInfoService.getServiceLog(id, unit, lines, level, keyword);
        return Result.ok(log);
    }
}
