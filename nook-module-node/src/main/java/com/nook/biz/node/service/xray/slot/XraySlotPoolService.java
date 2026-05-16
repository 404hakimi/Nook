package com.nook.biz.node.service.xray.slot;

import com.nook.biz.node.dal.dataobject.slot.XraySlotPoolDO;

import java.util.List;

/**
 * Xray Slot 池 Service 接口
 *
 * @author nook
 */
public interface XraySlotPoolService {

    /**
     * 初始化 Slot 池, 幂等
     *
     * <p>已存在的 slot 行不动, 缺失的补齐到 poolSize.
     *
     * @param serverId resource_server.id
     * @param poolSize slot 池大小 (1..200)
     */
    void initSlotPool(String serverId, int poolSize);

    /**
     * 原子分配一个空闲 slot 给指定 client; 调用方必须在 @Transactional 中
     *
     * @param serverId resource_server.id
     * @param clientId 占用此 slot 的 xray_client.id (业务侧已生成)
     * @return 分配到的 slot 编号; 池子满时抛 BusinessException(SLOT_POOL_EXHAUSTED)
     */
    int allocateSlot(String serverId, String clientId);

    /**
     * 释放 slot (revoke / 回滚时调)
     *
     * @param serverId  resource_server.id
     * @param slotIndex slot 编号
     */
    void releaseSlot(String serverId, int slotIndex);

    /**
     * 获得指定 server 全部 slot (运维 dashboard 用)
     *
     * @param serverId resource_server.id
     * @return slot 行列表 (按 slot_index 升序)
     */
    List<XraySlotPoolDO> getSlotPoolList(String serverId);
}
