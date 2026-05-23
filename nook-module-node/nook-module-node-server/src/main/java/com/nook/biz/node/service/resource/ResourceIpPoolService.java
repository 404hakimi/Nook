package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolRuntimeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolSocks5DO;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * IP 池 Service 接口
 *
 * @author nook
 */
public interface ResourceIpPoolService {

    /**
     * 创建 IP 池条目
     *
     * @param createReqVO IP 池信息
     * @return IP 池编号
     */
    String createIpPool(ResourceIpPoolSaveReqVO createReqVO);

    /**
     * 更新 IP 池条目 (整段表单, 含子表); socks5Password / sshPassword 留空 = 保留旧值.
     *
     * @param id          IP 池编号
     * @param updateReqVO IP 池信息
     */
    void updateIpPool(String id, ResourceIpPoolSaveReqVO updateReqVO);

    /**
     * 更新核心字段 (主表: region / ipTypeId / ipAddress / provisionMode / remark);
     * lifecycle 走 transition 接口, 子表 (SSH/账面/socks5) 走各自子 Service.
     *
     * @param id    IP 池编号
     * @param reqVO 核心字段
     */
    void updateCore(String id, ResourceIpPoolCoreUpdateReqVO reqVO);

    /**
     * 删除 IP 池条目
     *
     * @param id IP 池编号
     */
    void deleteIpPool(String id);

    /**
     * 按 id 查 IP 池条目; 必查到走 {@link com.nook.biz.node.validator.ResourceIpPoolValidator#validateExists}.
     *
     * @param id IP 池编号
     * @return IP 池条目; 不存在返 null
     */
    ResourceIpPoolDO getIpPool(String id);

    /**
     * 获得 IP 池分页
     *
     * @param pageReqVO 分页条件
     * @return IP 池分页
     */
    PageResult<ResourceIpPoolDO> getIpPoolPage(ResourceIpPoolPageReqVO pageReqVO);

    /**
     * 按 region + ipType 自动挑一个 AVAILABLE 条目并原子占用; 失败重试一次仍冲突抛 IP_POOL_OCCUPY_CONFLICT.
     *
     * @param region       区域过滤; 可空
     * @param ipTypeId     IP 类型过滤; 可空
     * @param memberUserId 占用方会员 id
     * @return 已占用的 IP 实体
     */
    ResourceIpPoolDO occupyOne(String region, String ipTypeId, String memberUserId);

    /**
     * 按指定 ipId 原子占用; 非 AVAILABLE 抛 IP_POOL_NOT_AVAILABLE.
     *
     * @param id           IP 池编号
     * @param memberUserId 占用方会员 id
     * @return 已占用的 IP 实体
     */
    ResourceIpPoolDO occupyById(String id, String memberUserId);

    /**
     * 退订 (user-facing): 状态切到 cooling, 到期由 {@link #sweepExpiredCooling} 回到 available.
     *
     * <p>独立事务 (REQUIRES_NEW), 带 bound-client 守卫 — 若 IP 仍被 client 引用直接抛错,
     * 防 controller 端手动退订把 IP 跟 client 解绑造成漂移.
     *
     * @param id IP 池编号
     */
    void releaseToCooling(String id);

    /**
     * 退订 (revoke 链路内部用): 跟外层事务共享 (REQUIRED), 不做 bound-client 校验.
     *
     * <p>跟 {@link #releaseToCooling} 行为一致, 但去掉 client 引用校验. revoke 主流程在外层
     * 事务里先 deleteById 删 client, 用 REQUIRES_NEW 看不到外层未 commit 的 delete, 反而误判
     * client 仍存在抛错, 导致 IP 状态停在 occupied 不切 cooling.
     *
     * <p>仅供 ClientOpExecutor 调; controller / job 等外部入口请用 {@link #releaseToCooling}.
     *
     * @param id IP 池编号
     */
    void releaseToCoolingForRevoke(String id);

    /**
     * 扫冷却到期条目回到 available; 调度器定时调用.
     *
     * @return 本次回收条数
     */
    int sweepExpiredCooling();

    /**
     * 批量获得 ipAddress Map (list enrich 用); 不下发 socks5 凭据.
     *
     * @param ids IP 池编号集合
     * @return Map of ipId → ipAddress
     */
    Map<String, String> getIpAddressMap(Collection<String> ids);

    /**
     * 批量获得 IP 池整行 (含 socks5 凭据); replay / sync 一次性预拉避免 N+1.
     *
     * @param ids IP 池编号集合
     * @return Map of ipId → ResourceIpPoolDO
     */
    Map<String, ResourceIpPoolDO> getIpPoolMap(Collection<String> ids);

    /**
     * 切换 lifecycle_state; 流转规则同 server (INSTALLING ↔ READY ↔ LIVE → RETIRED, RETIRED → LIVE 可复活).
     *
     * @param id       IP 池编号
     * @param newState 目标 lifecycle (INSTALLING / READY / LIVE / RETIRED)
     */
    void transitionLifecycle(String id, String newState);

    /**
     * 取 SSH 凭据子表
     *
     * @param ipId IP 池编号
     * @return 凭据 DO; 不存在返 null
     */
    ResourceIpPoolCredentialDO getCredential(String ipId);

    /**
     * 取账面子表
     *
     * @param ipId IP 池编号
     * @return 账面 DO; 不存在返 null
     */
    ResourceIpPoolBillingDO getBilling(String ipId);

    /**
     * 取 dante 配置 + 限速子表
     *
     * @param ipId IP 池编号
     * @return socks5 DO; 不存在返 null
     */
    ResourceIpPoolSocks5DO getSocks5(String ipId);

    /**
     * 取 agent 心跳 / 健康子表
     *
     * @param ipId IP 池编号
     * @return runtime DO; 不存在返 null
     */
    ResourceIpPoolRuntimeDO getRuntime(String ipId);

    /**
     * 批量取 4 张子表 (list 渲染用); 缺失子表行不在 map 里
     *
     * @param ipIds IP 池编号集合
     * @return 4 个 Map: ipId → 子 DO
     */
    SubtablesBundle batchLoadSubtables(Collection<String> ipIds);

    /** 4 张子表批量返回包. */
    record SubtablesBundle(
            Map<String, ResourceIpPoolCredentialDO> credentials,
            Map<String, ResourceIpPoolBillingDO> billings,
            Map<String, ResourceIpPoolSocks5DO> socks5s,
            Map<String, ResourceIpPoolRuntimeDO> runtimes) { }
}
