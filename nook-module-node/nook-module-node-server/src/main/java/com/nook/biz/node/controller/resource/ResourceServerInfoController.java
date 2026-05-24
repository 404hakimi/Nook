package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.SystemdStatusRespVO;
import com.nook.biz.node.service.resource.ResourceServerInfoService;
import com.nook.common.web.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - 服务器只读检视 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server")
@Validated
@RequiredArgsConstructor
public class ResourceServerInfoController {

    private final ResourceServerInfoService resourceServerInfoService;

    /**
     * 探活 server (SSH 跑 'true' 验证可达性); 失败包成 success=false 不抛异常
     *
     * @param id server 编号
     * @return 探活结果
     */
    @PostMapping("/connectivity-test")
    public Result<ConnectivityTestRespVO> testConnectivity(@RequestParam("id") String id) {
        ConnectivityTestRespVO result = resourceServerInfoService.testConnectivity(id);
        return Result.ok(result);
    }

    /**
     * 获得操作系统级基本信息 (hostname / 内存 / 磁盘 / 时区 等)
     *
     * @param id server 编号
     * @return 系统信息
     */
    @GetMapping("/system-info")
    public Result<ServerSystemInfoRespVO> getSystemInfo(@RequestParam("id") String id) {
        ServerSystemInfoRespVO info = resourceServerInfoService.getSystemInfo(id);
        return Result.ok(info);
    }

    /**
     * 获得 UFW 防火墙状态 (ufw status verbose 原文); 未装 ufw 时回提示文案
     *
     * @param id server 编号
     * @return ufw status 多行字符串
     */
    @GetMapping("/ufw-status")
    public Result<String> getUfwStatus(@RequestParam("id") String id) {
        return Result.ok(resourceServerInfoService.getUfwStatus(id));
    }

    /**
     * 获得指定 systemd unit 的通用状态 (active / 启动时间 / 开机自启)
     *
     * @param id   server 编号
     * @param unit systemd unit 名 (如 xray / danted)
     * @return systemd 状态
     */
    @GetMapping("/systemd-status")
    public Result<SystemdStatusRespVO> getSystemdStatus(@RequestParam("id") String id,
                                                        @RequestParam("unit") String unit) {
        SystemdStatusRespVO status = resourceServerInfoService.getSystemdStatus(id, unit);
        return Result.ok(status);
    }

    /**
     * 获得 systemd unit 的 journalctl 日志, 按行数 + 级别 + 关键词过滤
     *
     * @param id      server 编号
     * @param unit    systemd unit 名
     * @param lines   行数 (默认 100, 上限 5000)
     * @param level   级别过滤 (all / warning / err)
     * @param keyword 关键词子串过滤
     * @return 日志结果
     */
    @GetMapping("/service-log")
    public Result<ServiceLogRespVO> getServiceLog(@RequestParam("id") String id,
                                                  @RequestParam("unit") String unit,
                                                  @RequestParam(value = "lines", required = false) Integer lines,
                                                  @RequestParam(value = "level", required = false) String level,
                                                  @RequestParam(value = "keyword", required = false) String keyword) {
        ServiceLogRespVO log = resourceServerInfoService.getServiceLog(id, unit, lines, level, keyword);
        return Result.ok(log);
    }

    /**
     * SSH 列出远端网卡 (排除 lo); agent 装机 NIC interface 下拉用; 失败返空 list
     *
     * @param id server 编号
     * @return 网卡名列表
     */
    @GetMapping("/network-interfaces")
    public Result<List<String>> listNetworkInterfaces(@RequestParam("id") String id) {
        return Result.ok(resourceServerInfoService.listNetworkInterfaces(id));
    }
}
