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
     * 获取 Xray安装信息
     *
     * @param serverId 服务器ID
     * @return XrayInstallRespDTO
     */
    XrayInstallRespDTO getXrayInstall(String serverId);

    /**
     * 检查是否有绑定该域名的 Xray
     *
     * @param domainId 域名ID
     * @return boolean
     */
    boolean isDomainBound(String domainId);
}
