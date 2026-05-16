package com.nook.biz.node.service.xray.slot;

import com.nook.biz.node.dal.dataobject.slot.XraySlotPoolDO;
import com.nook.biz.node.dal.mysql.mapper.XraySlotPoolMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Xray Slot 池 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class XraySlotPoolServiceImpl implements XraySlotPoolService {

    @Resource
    private XraySlotPoolMapper xraySlotPoolMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initSlotPool(String serverId, int poolSize) {
        // 幂等: 已存在的 slot 行不动, 只插缺失的; 这样运营重新部署 / 扩容池时能平滑追加
        Set<Integer> existing = xraySlotPoolMapper.selectExistingIndexes(serverId);
        int inserted = 0;
        for (int i = 1; i <= poolSize; i++) {
            if (existing.contains(i)) continue;
            XraySlotPoolDO row = new XraySlotPoolDO();
            row.setServerId(serverId);
            row.setSlotIndex(i);
            row.setUsed(0);
            xraySlotPoolMapper.insert(row);
            inserted++;
        }
        log.info("[slot-pool] init server={} poolSize={} 新插入={}", serverId, poolSize, inserted);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int allocateSlot(String serverId, String clientId) {
        // SELECT FOR UPDATE 锁住一个空闲 slot; 与同 server 的并发 allocate 会串行化
        XraySlotPoolDO slot = xraySlotPoolMapper.pickFreeSlotForUpdate(serverId);
        if (slot == null) {
            throw new BusinessException(XrayErrorCode.SLOT_POOL_EXHAUSTED, serverId);
        }
        int affected = xraySlotPoolMapper.occupy(serverId, slot.getSlotIndex(), clientId);
        if (affected != 1) {
            // 理论上 SELECT FOR UPDATE 锁住后 occupy 必成功, 走到这里说明锁实现异常
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    serverId, "slot occupy 失败 slot=" + slot.getSlotIndex() + " affected=" + affected);
        }
        log.info("[slot-pool] allocate server={} slot={} client={}",
                serverId, slot.getSlotIndex(), clientId);
        return slot.getSlotIndex();
    }

    @Override
    public void releaseSlot(String serverId, int slotIndex) {
        int affected = xraySlotPoolMapper.release(serverId, slotIndex);
        log.info("[slot-pool] release server={} slot={} affected={}", serverId, slotIndex, affected);
    }

    @Override
    public List<XraySlotPoolDO> getSlotPoolList(String serverId) {
        return xraySlotPoolMapper.selectByServerId(serverId);
    }
}
