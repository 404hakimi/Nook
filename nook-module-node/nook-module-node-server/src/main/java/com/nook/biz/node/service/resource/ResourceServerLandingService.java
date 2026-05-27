package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCapacityUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * SOCKS5 落地节点 Service 接口
 *
 * @author nook
 */
public interface ResourceServerLandingService {

    /**
     * 初始化 landing 子表 + dante install 默认值; 由统一创建 server 流程调用
     *
     * @param serverId 落地节点编号
     * @param ipTypeId IP 类型 FK
     */
    void initSubtables(String serverId, String ipTypeId);

    /**
     * 删除落地节点
     *
     * @param id 落地节点编号
     */
    void delete(String id);

    /**
     * 更新落地节点核心字段
     *
     * @param id    落地节点编号
     * @param reqVO 核心字段入参
     */
    void updateCore(String id, ServerLandingCoreUpdateReqVO reqVO);

    /**
     * 更新落地节点 dante 配置
     *
     * @param id    落地节点编号
     * @param reqVO dante 配置入参
     */
    void updateSocks5(String id, ServerLandingSocks5UpdateReqVO reqVO);

    /**
     * 更新账面 (子表不存在则 insert)
     *
     * @param id    落地节点编号
     * @param reqVO 账面入参
     */
    void updateBilling(String id, ServerLandingBillingUpdateReqVO reqVO);

    /**
     * 更新容量阈值 (限速 / 月流量上限 / 重置策略; rxBytes/txBytes/throttleState 由 agent / 状态机维护)
     *
     * @param id    落地节点编号
     * @param reqVO 容量入参
     */
    void updateCapacity(String id, ServerLandingCapacityUpdateReqVO reqVO);

    /**
     * 获得落地节点主表
     *
     * @param id 落地节点编号
     * @return 主表
     */
    ResourceServerDO getServer(String id);

    /**
     * 获得落地节点子表
     *
     * @param id 落地节点编号
     * @return 落地节点子表
     */
    ResourceServerLandingDO getLanding(String id);

    /**
     * 获得账面子表
     *
     * @param id 落地节点编号
     * @return 账面
     */
    ResourceServerBillingDO getBilling(String id);

    /**
     * 获得容量子表
     *
     * @param id 落地节点编号
     * @return 容量
     */
    ResourceServerCapacityDO getCapacity(String id);

    /**
     * 获得运行时子表
     *
     * @param id 落地节点编号
     * @return 运行时
     */
    ResourceServerRuntimeDO getRuntime(String id);

    /**
     * 获得落地节点分页主表
     *
     * @param reqVO 分页条件
     * @return 主表分页
     */
    PageResult<ResourceServerDO> getPage(ServerLandingPageReqVO reqVO);

    /**
     * 获得落地节点总览统计
     *
     * @return key → count (total, lifecycle_INSTALLING/READY/LIVE/RETIRED, status_AVAILABLE/RESERVED/OCCUPIED/COOLING)
     */
    Map<String, Long> getSummary();

    /**
     * 占用落地节点
     *
     * @param serverId     落地节点编号
     * @param memberUserId 占用方编号
     * @return 落地节点主表
     */
    ResourceServerDO occupyById(String serverId, String memberUserId);

    /**
     * 退订落地节点至冷却
     *
     * @param serverId 落地节点编号
     */
    void releaseToCoolingForRevoke(String serverId);

    /**
     * 批量获得落地节点子表
     *
     * @param serverIds 落地节点编号集合
     * @return 落地节点编号 → 子表
     */
    Map<String, ResourceServerLandingDO> getLandingMap(Collection<String> serverIds);

    /**
     * 批量获得 5 张子表
     *
     * @param serverIds 落地节点编号集合
     * @return 子表批量返回包
     */
    SubtablesBundle batchLoadSubtables(Collection<String> serverIds);

    /** 4 张子表批量返回包 (SSH 凭据走公共 /admin/resource/server/get-credential, 不在此包). */
    record SubtablesBundle(
            Map<String, ResourceServerLandingDO> landings,
            Map<String, ResourceServerBillingDO> billings,
            Map<String, ResourceServerCapacityDO> capacities,
            Map<String, ResourceServerRuntimeDO> runtimes) { }
}
