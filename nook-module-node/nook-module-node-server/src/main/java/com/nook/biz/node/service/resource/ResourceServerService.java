package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;

import java.util.Collection;
import java.util.Map;

/**
 * 资源服务器 Service 接口
 *
 * @author nook
 */
public interface ResourceServerService {

    /**
     * 创建服务器
     *
     * @param createReqVO 创建入参
     * @return 服务器编号
     */
    String createServer(ResourceServerCreateReqVO createReqVO);

    /**
     * 更新服务器核心字段
     *
     * @param id    服务器编号
     * @param reqVO 更新入参
     */
    void updateCore(String id, ResourceServerCoreUpdateReqVO reqVO);

    /**
     * 删除服务器
     *
     * @param id 服务器编号
     */
    void deleteServer(String id);

    /**
     * 按区域统计机器数 (线路机 + 落地机)
     *
     * @return 区域码 → 机器数
     */
    Map<String, Long> countByRegion();

    /**
     * 获得服务器 (不存在返 null)
     *
     * @param id 服务器编号
     * @return 服务器
     */
    ResourceServerDO getServer(String id);

    /**
     * 获得服务器 (不存在抛 SERVER_NOT_FOUND)
     *
     * @param id 服务器编号
     * @return 服务器
     */
    ResourceServerDO requireServer(String id);

    /**
     * 批量获得服务器 DO
     *
     * @param ids 服务器编号集合
     * @return 服务器编号 → 服务器 DO
     */
    Map<String, ResourceServerDO> getServerMap(Collection<String> ids);

    /**
     * 批量获得服务器名称
     *
     * @param ids 服务器编号集合
     * @return 服务器编号 → 服务器名称
     */
    Map<String, String> getServerNameMap(Collection<String> ids);

    /**
     * 批量获得服务器 IP / 域名 (替代老 credentialService.getHostMap;
     * host 已经搬到 server.ip_address)
     *
     * @param ids 服务器编号集合
     * @return 服务器编号 → ip_address
     */
    Map<String, String> getIpAddressMap(Collection<String> ids);

}
