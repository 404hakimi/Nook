package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerFrontlineUpdateReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerQuotaDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;

import java.util.Collection;
import java.util.Map;

/**
 * 线路机扩展 Service 接口
 *
 * @author nook
 */
public interface ResourceServerFrontlineService {

    /**
     * 获得线路机扩展
     *
     * @param serverId 服务器编号
     * @return 线路机扩展
     */
    ResourceServerFrontlineDO get(String serverId);

    /**
     * 创建线路机扩展
     *
     * @param serverId 服务器编号
     * @param reqVO    入参
     */
    void create(String serverId, ResourceServerFrontlineUpdateReqVO reqVO);

    /**
     * 更新线路机扩展
     *
     * @param serverId 服务器编号
     * @param reqVO    入参
     */
    void update(String serverId, ResourceServerFrontlineUpdateReqVO reqVO);

    /**
     * 批量加载线路机列表的运行时聚合 (凭据 / 运行时 / 容量 / xray 实例)
     *
     * @param serverIds 服务器编号集合
     * @return 运行时聚合包
     */
    RuntimeBundle batchLoadRuntimeBundle(Collection<String> serverIds);

    /**
     * 加载单条线路机的运行时聚合
     *
     * @param serverId 线路机编号
     * @return 单条运行时聚合
     */
    RuntimeBundle loadRuntimeBundleSingle(String serverId);

    /** 线路机运行时聚合 (凭据 / 运行时 / 配额配置 / 当周期测量 / xray). */
    record RuntimeBundle(
            Map<String, ResourceServerCredentialDO> credentialMap,
            Map<String, ResourceServerRuntimeDO> runtimeMap,
            Map<String, ResourceServerQuotaDO> quotaMap,
            Map<String, ResourceServerTrafficDO> trafficMap,
            Map<String, XrayServerDO> xrayMap) { }
}
