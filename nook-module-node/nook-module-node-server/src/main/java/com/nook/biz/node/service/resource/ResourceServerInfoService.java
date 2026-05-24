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
     * 探活服务器
     *
     * @param serverId 服务器编号
     * @return 探活结果
     */
    ConnectivityTestRespVO testConnectivity(String serverId);

    /**
     * 获得操作系统级基本信息
     *
     * @param serverId 服务器编号
     * @return 系统信息
     */
    ServerSystemInfoRespVO getSystemInfo(String serverId);

    /**
     * 获得 UFW 防火墙状态
     *
     * @param serverId 服务器编号
     * @return ufw status 文本
     */
    String getUfwStatus(String serverId);

    /**
     * 获得 systemd unit 通用状态
     *
     * @param serverId 服务器编号
     * @param unit     systemd unit 名
     * @return systemd 状态
     */
    SystemdStatusRespVO getSystemdStatus(String serverId, String unit);

    /**
     * 获得 systemd unit 的 journalctl 日志
     *
     * @param serverId 服务器编号
     * @param unit     systemd unit 名
     * @param logLines 读取行数
     * @param logLevel 级别过滤
     * @param keyword  关键字过滤
     * @return 日志内容
     */
    ServiceLogRespVO getServiceLog(String serverId, String unit, Integer logLines, String logLevel, String keyword);

    /**
     * 获得远端网卡列表
     *
     * @param serverId 服务器编号
     * @return 网卡名列表
     */
    java.util.List<String> listNetworkInterfaces(String serverId);
}
