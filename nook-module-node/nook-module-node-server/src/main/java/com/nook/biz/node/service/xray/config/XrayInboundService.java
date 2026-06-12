package com.nook.biz.node.service.xray.config;

import com.nook.biz.node.entity.XrayInboundDO;

import java.util.Collection;
import java.util.Map;

/**
 * Xray inbound 共享配置 Service 接口
 *
 * @author nook
 */
public interface XrayInboundService {

    /**
     * 幂等写入 inbound 配置
     *
     * @param entity inbound 配置
     */
    void upsert(XrayInboundDO entity);

    /**
     * 按 serverId 取 inbound 配置
     *
     * @param serverId 服务器编号
     * @return inbound 配置
     */
    XrayInboundDO get(String serverId);

    /**
     * 按 serverId 批量取 inbound 配置
     *
     * @param serverIds 服务器编号集合
     * @return serverId → inbound 配置
     */
    Map<String, XrayInboundDO> listByServerIds(Collection<String> serverIds);
}
