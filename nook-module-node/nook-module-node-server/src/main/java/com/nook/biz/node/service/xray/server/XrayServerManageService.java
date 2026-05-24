package com.nook.biz.node.service.xray.server;

import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayServerStatusRespVO;

import java.util.function.Consumer;

/**
 * Xray 线路服务器管理 Service 接口
 *
 * @author nook
 */
public interface XrayServerManageService {

    /**
     * 流式装机 / 重装 xray
     *
     * @param serverId 服务器编号
     * @param reqVO    装机入参
     * @param lineSink 每行 stdout 的消费回调
     */
    void installStreaming(String serverId, XrayServerInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 重启 xray 服务
     *
     * @param serverId 服务器编号
     * @return 远端 stdout
     */
    String restart(String serverId);

    /**
     * 获得 xray systemd 服务状态
     *
     * @param serverId 服务器编号
     * @return xray 服务状态
     */
    XrayServerStatusRespVO getXraySystemdStatus(String serverId);

    /**
     * 切换 xray 开机自启
     *
     * @param serverId 服务器编号
     * @param enabled  是否开机自启
     * @return 远端 stdout
     */
    String setAutostart(String serverId, boolean enabled);

    /**
     * 获得 xray 日志文件内容
     *
     * @param serverId 服务器编号
     * @param variant  日志变体 (access / error)
     * @param lines    读取行数
     * @param keyword  关键字过滤
     * @return 日志内容
     */
    ServiceLogRespVO getXrayLogFile(String serverId, String variant, Integer lines, String keyword);
}
