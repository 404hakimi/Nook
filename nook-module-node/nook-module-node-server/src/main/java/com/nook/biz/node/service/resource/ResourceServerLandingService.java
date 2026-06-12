package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.landing.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingQuotaUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingListItemRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.entity.ResourceServerBillingDO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.entity.ResourceServerRuntimeDO;
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
     * 更新容量阈值 (限速 / 月流量上限 / 重置策略; 实际用量与限流状态不在此更新)
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
    Socks5InstallDO getLanding(String id);

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
     * 落地机分页 (连表出按需列表项视图: 占用 / IP 类型 / socks5 / 账单到期 / 配额 / 在线态)
     *
     * @param reqVO 分页条件
     * @return 列表项视图分页
     */
    PageResult<ServerLandingListItemRespVO> getLandingPage(ServerLandingPageReqVO reqVO);

    /**
     * 获得落地节点总览统计
     *
     * @return key → count (total, lifecycle_INSTALLING/READY/LIVE/RETIRED, status_AVAILABLE/OCCUPIED)
     */
    Map<String, Long> getSummary();

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
    Map<String, Socks5InstallDO> getLandingMap(Collection<String> serverIds);

    /**
     * 切换落地机生命周期 (停用前置: 在用不可停)
     *
     * @param id       落地机编号
     * @param newState 目标生命周期
     */
    void transitionLifecycle(String id, String newState);
}
