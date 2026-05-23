package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.SystemdStatusRespVO;

/**
 * 服务器信息 Service 接口
 *
 * @author nook
 */
public interface ResourceServerInfoService {

    /**
     * 主机可达性探活, 失败包成 success=false 不抛异常
     *
     * @param serverId resource_server.id
     * @return 探活结果
     */
    ConnectivityTestRespVO testConnectivity(String serverId);

    /**
     * 获得操作系统级基本信息 (hostname / 内存 / 磁盘 等); 不依赖 Xray 是否在跑
     *
     * @param serverId resource_server.id
     * @return 系统信息
     */
    ServerSystemInfoRespVO getSystemInfo(String serverId);

    /**
     * 获得 UFW 防火墙状态 (ufw status verbose 原文); 未装 ufw 时回提示文案
     *
     * @param serverId resource_server.id
     * @return ufw status 多行字符串
     */
    String getUfwStatus(String serverId);

    /**
     * 获得指定 systemd unit 的通用状态 (active / 启动时间 / 开机自启)
     *
     * @param serverId resource_server.id
     * @param unit     systemd unit 名
     * @return systemd 状态
     */
    SystemdStatusRespVO getSystemdStatus(String serverId, String unit);

    /**
     * 获得指定 systemd unit 的 journalctl 日志, 支持关键词过滤
     *
     * @param serverId resource_server.id
     * @param unit     systemd unit 名
     * @param logLines 行数 (默认 100, 上限 5000)
     * @param logLevel 级别过滤 (all / warning / err)
     * @param keyword  关键词子串过滤 (大小写不敏感); 空表示不过滤
     * @return 日志结果
     */
    ServiceLogRespVO getServiceLog(String serverId, String unit, Integer logLines, String logLevel, String keyword);

    /**
     * SSH 列出远端网卡 (排除 lo); agent 装机时 NIC interface 下拉用.
     *
     * @return 网卡名列表, 失败返空 list (前端 fallback 用 "auto")
     */
    java.util.List<String> listNetworkInterfaces(String serverId);
}
