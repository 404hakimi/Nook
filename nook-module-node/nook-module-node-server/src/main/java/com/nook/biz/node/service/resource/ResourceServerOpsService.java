package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ops.EnableSwapReqVO;
import com.nook.biz.node.framework.server.snapshot.ConnectivitySnapshot;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * 服务器运维 Service 接口 (信息查看 + 基础操作)
 *
 * @author nook
 */
public interface ResourceServerOpsService {

    /**
     * 启用 swap 分区 (流式)
     *
     * @param serverId 服务器编号
     * @param reqVO    swap 入参
     * @return 流式响应
     */
    ResponseBodyEmitter enableSwapStream(String serverId, EnableSwapReqVO reqVO);

    /**
     * 启用 BBR 拥塞控制 (流式)
     *
     * @param serverId 服务器编号
     * @return 流式响应
     */
    ResponseBodyEmitter enableBbrStream(String serverId);

    /**
     * 探活服务器
     *
     * @param serverId 服务器编号
     * @return 探活快照
     */
    ConnectivitySnapshot testConnectivity(String serverId);

    /**
     * 获得操作系统级基本信息
     *
     * @param serverId 服务器编号
     * @return 主机信息快照
     */
    HostInfoSnapshot getSystemInfo(String serverId);

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
     * @return systemd 状态快照
     */
    SystemdStatusSnapshot getSystemdStatus(String serverId, String unit);

    /**
     * 获得 systemd unit journalctl 日志
     *
     * @param serverId 服务器编号
     * @param unit     systemd unit 名
     * @param lines    读取行数
     * @param level    级别过滤
     * @param keyword  关键字过滤
     * @return 日志快照
     */
    JournalLogSnapshot getServiceLog(String serverId, String unit, Integer lines, String level, String keyword);

    /**
     * 获得远端网卡列表
     *
     * @param serverId 服务器编号
     * @return 网卡名列表
     */
    List<String> listNetworkInterfaces(String serverId);
}
