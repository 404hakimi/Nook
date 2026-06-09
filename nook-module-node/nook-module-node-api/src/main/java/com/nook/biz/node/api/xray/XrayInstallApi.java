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
     * 获取服务器的 xray 实例元数据
     *
     * @param serverId 服务器ID
     * @return xray 实例 DTO; 未安装 xray 返 null
     */
    XrayInstallRespDTO getByServerId(String serverId);

    /**
     * 批量取服务器的 xray 实例元数据
     *
     * @param serverIds 服务器ID集合
     * @return 服务器ID → xray 实例 DTO (未安装的不在 map 内)
     */
    Map<String, XrayInstallRespDTO> listByServerIds(Collection<String> serverIds);

    /**
     * 该根域 (system_domain.id) 是否已被任意线路机的 xray 实例绑定
     *
     * @param domainId 根域 id
     * @return true = 有线路机绑定 (改根域串会致已部署机器 FQDN 漂移, 应拒绝)
     */
    boolean isDomainBound(String domainId);
}
