package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.landing.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingQuotaUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerQuotaDO;
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
     * 初始化落地节点子表与 dante 安装默认值
     *
     * @param serverId 落地节点ID
     * @param ipTypeId IP 类型ID
     */
    void initSubtables(String serverId, String ipTypeId);

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
     * 更新容量阈值 (限速 / 月流量上限 / 重置策略)
     *
     * <p>实际用量与限流状态由 agent 与状态机维护, 不在此更新.
     *
     * @param id    落地节点ID
     * @param reqVO 容量入参
     */
    void updateQuota(String id, ServerLandingQuotaUpdateReqVO reqVO);

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
    ResourceServerQuotaDO getQuota(String id);

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
     * @return key → count (total, lifecycle_INSTALLING/READY/LIVE/RETIRED, status_AVAILABLE/OCCUPIED/RESERVED)
     */
    Map<String, Long> getSummary();

    /**
     * 占用落地节点 (条件更新, 仅 AVAILABLE 可占)
     *
     * @param serverId     落地节点编号
     * @param memberUserId 占用方编号
     * @return true=占用成功; false=被并发抢占 / 非 AVAILABLE
     */
    boolean occupyById(String serverId, String memberUserId);

    /**
     * 退订释放落地节点 (直接转为可用, 立即可再分配)
     *
     * @param serverId 落地节点编号
     */
    void releaseForRevoke(String serverId);

    /**
     * 批量获得落地节点主表
     *
     * @param serverIds 落地节点编号集合
     * @return 落地节点编号 → 主表
     */
    Map<String, ResourceServerDO> getServerMap(Collection<String> serverIds);

    /**
     * 批量获得落地节点子表
     *
     * @param serverIds 落地节点编号集合
     * @return 落地节点编号 → 子表
     */
    Map<String, ResourceServerLandingDO> getLandingMap(Collection<String> serverIds);

    /**
     * 批量获得账面子表
     *
     * @param serverIds 落地节点编号集合
     * @return 落地节点编号 → 账面
     */
    Map<String, ResourceServerBillingDO> getBillingMap(Collection<String> serverIds);

    /**
     * 批量获得配额子表
     *
     * @param serverIds 落地节点编号集合
     * @return 落地节点编号 → 配额
     */
    Map<String, ResourceServerQuotaDO> getQuotaMap(Collection<String> serverIds);

    /**
     * 批量获得运行时子表
     *
     * @param serverIds 落地节点编号集合
     * @return 落地节点编号 → 运行时
     */
    Map<String, ResourceServerRuntimeDO> getRuntimeMap(Collection<String> serverIds);
}
