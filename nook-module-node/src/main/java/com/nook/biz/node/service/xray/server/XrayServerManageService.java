package com.nook.biz.node.service.xray.server;

import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayServerStatusRespVO;

import java.util.function.Consumer;

/**
 * Xray 线路服务器管理 Service 接口
 *
 * <p>负责 Xray 部署 / 重启 / 状态查询 / 开机自启开关.
 *
 * @author nook
 */
public interface XrayServerManageService {

    /**
     * 流式安装/重装 Xray, 部署脚本一般 1-5 分钟.
     *
     * @param serverId resource_server.id
     * @param reqVO    安装参数
     * @param lineSink 每行 stdout 的消费回调
     */
    void installStreaming(String serverId, XrayServerInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 重启 Xray 服务, 客户连接会断 1-2 秒.
     *
     * @param serverId resource_server.id
     * @return 远端 stdout (含 is-active + xray version)
     */
    String restart(String serverId);

    /**
     * 获取 Xray 服务状态
     *
     * @param serverId 服务器ID
     * @return ServiceStatusRespVO
     */
    XrayServerStatusRespVO getXraySystemdStatus(String serverId);

    /**
     * 开/关 Xray 开机自启 (systemctl enable/disable), 末尾返回 is-enabled 结果给前端确认.
     *
     * @param serverId resource_server.id
     * @param enabled  true=enable, false=disable
     * @return 远端 stdout
     */
    String setAutostart(String serverId, boolean enabled);

    /**
     * 拉 xray 自己的日志文件 (access.log 或 error.log, 文件路径 = xray_node.xrayLogDir/{variant}.log),
     * 跟 journalctl -u xray 互补 — journal 看启动失败, file 看真正的连接/错误.
     *
     * @param serverId resource_server.id
     * @param variant  "access" | "error"; access 看每个连接, error 看 xray 内部错误
     * @param lines    行数 (默认 100, 上限 5000)
     * @param keyword  关键词子串过滤
     * @return 日志快照; unit 字段填实际文件路径
     */
    ServiceLogRespVO getXrayLogFile(String serverId, String variant, Integer lines, String keyword);
}
