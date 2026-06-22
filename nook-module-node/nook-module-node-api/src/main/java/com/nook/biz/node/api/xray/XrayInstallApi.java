package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayInstallRespDTO;

import java.util.Collection;
import java.util.Map;

/**
 * Xray 实例 Api 接口
 *
 * @author nook
 */
public interface XrayInstallApi {

    /**
     * 获取 xray 安装信息; 未装 xray 返 null
     *
     * @param serverId 服务器ID
     * @return xray 安装信息
     */
    XrayInstallRespDTO getXrayInstall(String serverId);

    /**
     * 该域名是否已被任意线路机的 xray 绑定
     *
     * @param domainId 域名ID
     * @return 是否已绑定
     */
    boolean isDomainBound(String domainId);

    /**
     * 装机回报: 若该机 install_status 卡在 deploying 则推进到 ok (agent 心跳报 xray 已起时调);
     * 已是终态 (ok/failed) 不动. 兜底跨洲长部署同步连接中断后 status 卡死.
     *
     * @param serverId 服务器ID
     */
    void markDeployedIfDeploying(String serverId);
}
