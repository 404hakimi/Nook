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
}
