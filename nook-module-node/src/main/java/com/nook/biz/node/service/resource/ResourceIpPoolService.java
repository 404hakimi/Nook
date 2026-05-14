package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
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
     * 更新 IP 池条目; socks5Password 留空 = 保留旧值.
     *
     * @param id          IP 池编号
     * @param updateReqVO IP 池信息
     */
    void updateIpPool(String id, ResourceIpPoolSaveReqVO updateReqVO);

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
     * 退订: 状态切到 cooling, 到期由 {@link #sweepExpiredCooling} 回到 available.
     *
     * @param id IP 池编号
     */
    void releaseToCooling(String id);

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
}
