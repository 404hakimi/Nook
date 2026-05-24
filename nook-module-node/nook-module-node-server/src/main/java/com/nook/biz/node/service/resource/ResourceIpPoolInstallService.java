package com.nook.biz.node.service.resource;

import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolInstallDO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * SOCKS5 装机事实 Service 接口
 *
 * @author nook
 */
public interface ResourceIpPoolInstallService {

    /**
     * 幂等写入装机事实
     *
     * @param entity 装机事实
     */
    void upsert(ResourceIpPoolInstallDO entity);

    /**
     * 获得装机事实
     *
     * @param ipId IP 池编号
     * @return 装机事实
     */
    ResourceIpPoolInstallDO get(String ipId);

    /**
     * 批量获得装机事实
     *
     * @param ipIds IP 池编号集合
     * @return IP 池编号 → 装机事实
     */
    Map<String, ResourceIpPoolInstallDO> listByIpIds(Collection<String> ipIds);

    /**
     * 更新 dante 启动时间
     *
     * @param ipId   IP 池编号
     * @param uptime 探测到的 dante 启动时间
     */
    void updateDanteUptime(String ipId, LocalDateTime uptime);
}
