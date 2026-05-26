package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerFrontlineUpdateReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;

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
     * 批量加载线路机列表的运行时聚合 (credential / runtime / capacity / xray; agent_runtime_config 由 agent 模块跨模块查)
     *
     * @param serverIds server id 集合
     * @return 运行时聚合包
     */
    RuntimeBundle batchLoadRuntimeBundle(Collection<String> serverIds);

    /**
     * 加载单条线路机的运行时聚合 (detail 页 header 用; 等价 batchLoad 单条收敛)
     *
     * @param serverId 线路机编号
     * @return 单条运行时聚合
     */
    RuntimeBundle loadRuntimeBundleSingle(String serverId);

    /** 列表运行时聚合 (4 个子表 Map; agent_runtime_config 跨模块查不在此). */
    record RuntimeBundle(
            Map<String, ResourceServerCredentialDO> credentialMap,
            Map<String, ResourceServerRuntimeDO> runtimeMap,
            Map<String, ResourceServerCapacityDO> capacityMap,
            Map<String, XrayServerDO> xrayMap) { }
}
