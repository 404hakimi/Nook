package com.nook.biz.node.service.server;

import com.nook.biz.node.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.server.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.server.vo.SystemdStatusRespVO;

/** 服务器只读检视业务出口; 调 framework.ServerProbe 后转 VO 给 controller. */
public interface ServerInspectorService {

    /** 探活; 失败已被包成 success=false 结构化结果, 不抛异常. */
    ConnectivityTestRespVO testConnectivity(String serverId);

    /** OS 级基本信息 (hostname / 内存 / 磁盘 等). */
    ServerSystemInfoRespVO getSystemInfo(String serverId);

    /** 指定 systemd unit 的通用运行状态 (active / 启动时间 / 是否开机自启); 不含 service 专属字段如 version/listening. */
    SystemdStatusRespVO getSystemdStatus(String serverId, String unit);

    /** 指定 systemd unit 的 journalctl 日志; lines 默认 100 上限 5000, level 取 all/warning/err. */
    ServiceLogRespVO getLog(String serverId, String unit, Integer logLines, String logLevel);
}
