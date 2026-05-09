package com.nook.biz.node.service.xray.server;

import com.nook.biz.node.controller.xray.server.vo.LineServerInstallReqVO;
import com.nook.biz.node.controller.xray.server.vo.ServiceStatusRespVO;

import java.util.function.Consumer;

/** Xray 线路服务器一站式管理: 部署 / 重启 / 状态查询 / 开机自启开关. */
public interface XrayServerManageService {

    /** 流式安装/重装 Xray; 部署脚本一般 1-5 分钟, 整体超时 10 分钟. */
    void installStreaming(String serverId, LineServerInstallReqVO reqVO, Consumer<String> lineSink);

    /** 重启 Xray 服务; 客户连接会断 1-2 秒. 返回 stdout (含 is-active + version). */
    String restart(String serverId);

    /** 查询 Xray 服务状态: active / version / 启动时间 / 监听端口 / 开机自启状态. */
    ServiceStatusRespVO status(String serverId);

    /** 开关开机自启 (systemctl enable / disable); 返回 stdout. */
    String setAutostart(String serverId, boolean enabled);
}
