package com.nook.biz.node.service.server;

import com.nook.biz.node.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.server.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.server.vo.SystemdStatusRespVO;

/**
 * 服务器只读检视业务出口, 调 framework.ServerProbe 后转 VO 给 controller.
 *
 * @author nook
 */
public interface ServerInspectorService {

    /**
     * 主机可达性探活, 失败包成 success=false 不抛异常.
     *
     * @param serverId resource_server.id
     * @return ConnectivityTestRespVO
     */
    ConnectivityTestRespVO testConnectivity(String serverId);

    /**
     * 操作系统级基本信息 (hostname / 内存 / 磁盘 等), 不依赖 Xray 是否在跑.
     *
     * @param serverId resource_server.id
     * @return ServerSystemInfoRespVO
     */
    ServerSystemInfoRespVO getSystemInfo(String serverId);

    /**
     * 指定 systemd unit 的通用状态 (active / 启动时间 / 是否开机自启), 不含 service 专属字段.
     *
     * @param serverId resource_server.id
     * @param unit     systemd unit 名
     * @return SystemdStatusRespVO
     */
    SystemdStatusRespVO getSystemdStatus(String serverId, String unit);

    /**
     * 指定 systemd unit 的 journalctl 日志.
     *
     * @param serverId resource_server.id
     * @param unit     systemd unit 名
     * @param logLines 行数 (默认 100, 上限 5000)
     * @param logLevel 级别过滤 (all / warning / err)
     * @return ServiceLogRespVO
     */
    ServiceLogRespVO getLog(String serverId, String unit, Integer logLines, String logLevel);
}
