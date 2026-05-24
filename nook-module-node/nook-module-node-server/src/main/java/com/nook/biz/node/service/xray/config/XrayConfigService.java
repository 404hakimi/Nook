package com.nook.biz.node.service.xray.config;

import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;

import java.util.Collection;
import java.util.Map;

/**
 * Xray inbound 共享配置 Service 接口
 *
 * @author nook
 */
public interface XrayConfigService {

    /**
     * 幂等写入 inbound 配置
     *
     * @param entity inbound 配置
     */
    void upsert(XrayConfigDO entity);

    /**
     * 按 serverId 取 inbound 配置
     *
     * @param serverId 服务器编号
     * @return inbound 配置
     */
    XrayConfigDO get(String serverId);

    /**
     * 按 serverId 批量取 inbound 配置
     *
     * @param serverIds 服务器编号集合
     * @return serverId → inbound 配置
     */
    Map<String, XrayConfigDO> listByServerIds(Collection<String> serverIds);
}
