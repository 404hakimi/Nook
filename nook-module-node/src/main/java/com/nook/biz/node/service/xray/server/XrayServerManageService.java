package com.nook.biz.node.service.xray.server;

import com.nook.biz.node.controller.xray.server.vo.LineServerInstallReqVO;
import com.nook.biz.node.controller.xray.server.vo.ServiceStatusRespVO;

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
    void installStreaming(String serverId, LineServerInstallReqVO reqVO, Consumer<String> lineSink);

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
    ServiceStatusRespVO getXraySystemdStatus(String serverId);

    /**
     * 开/关 Xray 开机自启 (systemctl enable/disable), 末尾返回 is-enabled 结果给前端确认.
     *
     * @param serverId resource_server.id
     * @param enabled  true=enable, false=disable
     * @return 远端 stdout
     */
    String setAutostart(String serverId, boolean enabled);
}
