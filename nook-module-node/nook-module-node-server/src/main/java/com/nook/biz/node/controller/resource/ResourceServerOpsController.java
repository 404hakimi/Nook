package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ops.ConnectivityTestRespVO;
import com.nook.biz.node.controller.resource.vo.ops.EnableSwapReqVO;
import com.nook.biz.node.controller.resource.vo.ops.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.ops.SystemdStatusRespVO;
import com.nook.biz.node.service.resource.ResourceServerOpsService;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * 管理后台 - 服务器运维 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server")
@Validated
public class ResourceServerOpsController {

    @Resource
    private ResourceServerOpsService resourceServerOpsService;

    /**
     * 启用 swap 分区 (流式)
     *
     * @param id    server 编号
     * @param reqVO swap 入参
     * @return 流式响应
     */
    @PostMapping(value = "/enable-swap", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter enableSwap(@RequestParam("id") String id,
                                          @Valid @RequestBody EnableSwapReqVO reqVO) {
        return resourceServerOpsService.enableSwapStream(id, reqVO);
    }

    /**
     * 启用 BBR 拥塞控制 (流式)
     *
     * @param id server 编号
     * @return 流式响应
     */
    @PostMapping(value = "/enable-bbr", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter enableBbr(@RequestParam("id") String id) {
        return resourceServerOpsService.enableBbrStream(id);
    }

    /**
     * 探活服务器
     *
     * @param id server 编号
     * @return 探活结果
     */
    @PostMapping("/test-connectivity")
    public Result<ConnectivityTestRespVO> testConnectivity(@RequestParam("id") String id) {
        return Result.ok(resourceServerOpsService.testConnectivity(id));
    }

    /**
     * 获得操作系统级基本信息
     *
     * @param id server 编号
     * @return 系统信息
     */
    @GetMapping("/get-system-info")
    public Result<ServerSystemInfoRespVO> getSystemInfo(@RequestParam("id") String id) {
        return Result.ok(resourceServerOpsService.getSystemInfo(id));
    }

    /**
     * 获得 UFW 防火墙状态
     *
     * @param id server 编号
     * @return ufw status 文本
     */
    @GetMapping("/get-ufw-status")
    public Result<String> getUfwStatus(@RequestParam("id") String id) {
        return Result.ok(resourceServerOpsService.getUfwStatus(id));
    }

    /**
     * 获得 systemd unit 通用状态
     *
     * @param id   server 编号
     * @param unit systemd unit 名
     * @return systemd 状态
     */
    @GetMapping("/get-systemd-status")
    public Result<SystemdStatusRespVO> getSystemdStatus(@RequestParam("id") String id,
                                                        @RequestParam("unit") String unit) {
        return Result.ok(resourceServerOpsService.getSystemdStatus(id, unit));
    }

    /**
     * 获得 systemd unit journalctl 日志
     *
     * @param id      server 编号
     * @param unit    systemd unit 名
     * @param lines   行数
     * @param level   级别过滤
     * @param keyword 关键词过滤
     * @return 日志
     */
    @GetMapping("/get-service-log")
    public Result<ServiceLogRespVO> getServiceLog(@RequestParam("id") String id,
                                                  @RequestParam("unit") String unit,
                                                  @RequestParam(value = "lines", required = false) Integer lines,
                                                  @RequestParam(value = "level", required = false) String level,
                                                  @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(resourceServerOpsService.getServiceLog(id, unit, lines, level, keyword));
    }

    /**
     * 列出远端网卡 (排除 lo)
     *
     * @param id server 编号
     * @return 网卡名列表
     */
    @GetMapping("/list-network-interface")
    public Result<List<String>> listNetworkInterfaces(@RequestParam("id") String id) {
        return Result.ok(resourceServerOpsService.listNetworkInterfaces(id));
    }
}
