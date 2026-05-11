package com.nook.biz.node.service.xray.slot;

import com.nook.biz.node.dal.dataobject.slot.XraySlotPoolDO;

import java.util.List;

/**
 * Xray 1:1 slot 池业务服务.
 *
 * @author nook
 */
public interface XraySlotPoolService {

    /**
     * 部署后初始化 slot 池, 幂等; 已存在的 slot 行不动, 缺失的补齐到 poolSize.
     *
     * @param serverId server
     * @param poolSize slot 池大小 (1..200)
     */
    void initialize(String serverId, int poolSize);

    /**
     * 原子分配一个空闲 slot 给指定 client; 调用方必须在 @Transactional 中.
     *
     * @param serverId server
     * @param clientId 占用此 slot 的 xray_client.id (业务侧已生成)
     * @return 分配到的 slot 编号; 池子满时抛 BusinessException(SLOT_POOL_EXHAUSTED)
     */
    int allocate(String serverId, String clientId);

    /**
     * 释放 slot (revoke / 回滚时调).
     *
     * @param serverId  server
     * @param slotIndex slot 编号
     */
    void release(String serverId, int slotIndex);

    /**
     * 列指定 server 全部 slot (运维 dashboard 用).
     *
     * @param serverId server
     * @return slot 行列表 (按 slot_index 升序)
     */
    List<XraySlotPoolDO> listByServerId(String serverId);
}
