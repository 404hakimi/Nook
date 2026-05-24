package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolInstallDO;
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
     * 更新 IP 池核心字段
     *
     * @param id    IP 池编号
     * @param reqVO 核心字段入参
     */
    void updateCore(String id, ResourceIpPoolCoreUpdateReqVO reqVO);

    /**
     * 删除 IP 池条目
     *
     * @param id IP 池编号
     */
    void deleteIpPool(String id);

    /**
     * 获得 IP 池
     *
     * @param id IP 池编号
     * @return IP 池
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
     * 按 region + ipType 自动挑一个 IP 并原子占用
     *
     * @param region       区域过滤
     * @param ipTypeId     IP 类型过滤
     * @param memberUserId 占用方会员编号
     * @return 已占用的 IP
     */
    ResourceIpPoolDO occupyOne(String region, String ipTypeId, String memberUserId);

    /**
     * 按 IP 池编号原子占用
     *
     * @param id           IP 池编号
     * @param memberUserId 占用方会员编号
     * @return 已占用的 IP
     */
    ResourceIpPoolDO occupyById(String id, String memberUserId);

    /**
     * 退订到冷却态 (外部入口; 带 bound-client 守卫)
     *
     * @param id IP 池编号
     */
    void releaseToCooling(String id);

    /**
     * 退订到冷却态 (revoke 链路内部使用; 跟外层事务共享)
     *
     * @param id IP 池编号
     */
    void releaseToCoolingForRevoke(String id);

    /**
     * 扫冷却到期 IP 回到可用态
     *
     * @return 本次回收条数
     */
    int sweepExpiredCooling();

    /**
     * 批量获得 IP 地址
     *
     * @param ids IP 池编号集合
     * @return IP 池编号 → IP 地址
     */
    Map<String, String> getIpAddressMap(Collection<String> ids);

    /**
     * 批量获得 IP 池整行
     *
     * @param ids IP 池编号集合
     * @return IP 池编号 → IP 池 DO
     */
    Map<String, ResourceIpPoolDO> getIpPoolMap(Collection<String> ids);

    /**
     * 切换 IP 池 lifecycle 状态
     *
     * @param id       IP 池编号
     * @param newState 目标 lifecycle 状态
     */
    void transitionLifecycle(String id, String newState);

    /**
     * 获得 SSH 凭据子表
     *
     * @param ipId IP 池编号
     * @return 凭据子表
     */
    ResourceIpPoolCredentialDO getCredential(String ipId);

    /**
     * 获得账面子表
     *
     * @param ipId IP 池编号
     * @return 账面子表
     */
    ResourceIpPoolBillingDO getBilling(String ipId);

    /**
     * 获得 dante 配置 + 限速子表
     *
     * @param ipId IP 池编号
     * @return socks5 子表
     */
    ResourceIpPoolSocks5DO getSocks5(String ipId);

    /**
     * 获得 agent 心跳子表
     *
     * @param ipId IP 池编号
     * @return runtime 子表
     */
    ResourceIpPoolRuntimeDO getRuntime(String ipId);

    /**
     * 获得装机事实子表
     *
     * @param ipId IP 池编号
     * @return install 子表
     */
    ResourceIpPoolInstallDO getInstall(String ipId);

    /**
     * 获得 IP 池总览统计 (stats 卡片用; 按 lifecycle + status 双维度分组)
     *
     * @return key → count map (LIFECYCLE_INSTALLING, STATUS_AVAILABLE 等); 缺失值默认 0
     */
    Map<String, Long> getSummary();

    /**
     * 批量获得 5 张子表
     *
     * @param ipIds IP 池编号集合
     * @return 5 张子表批量返回包
     */
    SubtablesBundle batchLoadSubtables(Collection<String> ipIds);

    /** 5 张子表批量返回包 */
    record SubtablesBundle(
            Map<String, ResourceIpPoolCredentialDO> credentials,
            Map<String, ResourceIpPoolBillingDO> billings,
            Map<String, ResourceIpPoolSocks5DO> socks5s,
            Map<String, ResourceIpPoolInstallDO> installs,
            Map<String, ResourceIpPoolRuntimeDO> runtimes) { }
}
