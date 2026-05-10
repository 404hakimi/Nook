package com.nook.biz.node.service.xray.node;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.service.xray.slot.XraySlotPoolService;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class XrayNodeServiceImpl implements XrayNodeService {

    @Resource
    private XrayNodeMapper xrayNodeMapper;
    @Resource
    private XraySlotPoolService slotPoolService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(String serverId,
                       String xrayVersion,
                       int xrayApiPort,
                       String xrayLogDir,
                       int slotPoolSize,
                       int slotPortBase) {
        XrayNodeDO existing = xrayNodeMapper.selectById(serverId);
        if (ObjectUtil.isNotNull(existing)) {
            existing.setXrayVersion(xrayVersion);
            existing.setXrayApiPort(xrayApiPort);
            existing.setXrayLogDir(xrayLogDir);
            existing.setSlotPoolSize(slotPoolSize);
            existing.setSlotPortBase(slotPortBase);
            xrayNodeMapper.updateById(existing);
            log.info("[xray-node] update server={} version={} apiPort={} poolSize={}",
                    serverId, xrayVersion, xrayApiPort, slotPoolSize);
        } else {
            XrayNodeDO row = new XrayNodeDO();
            row.setServerId(serverId);
            row.setXrayVersion(xrayVersion);
            row.setXrayApiPort(xrayApiPort);
            row.setXrayLogDir(xrayLogDir);
            row.setSlotPoolSize(slotPoolSize);
            row.setSlotPortBase(slotPortBase);
            row.setInstalledAt(LocalDateTime.now());
            xrayNodeMapper.insert(row);
            log.info("[xray-node] insert server={} version={} apiPort={} poolSize={}",
                    serverId, xrayVersion, xrayApiPort, slotPoolSize);
        }

        // xray_node 行存在 ↔ slot 池已初始化是不可分的业务约束;
        // 同事务内 initialize 让 "半初始化" 状态 (有 xray_node 但 slot 池空) 物理上不可能出现.
        // initialize 自身幂等: 已存在的 slot 行不动, 缺失的补齐到 slot_pool_size.
        slotPoolService.initialize(serverId, slotPoolSize);
    }

    @Override
    public XrayNodeDO loadOrThrow(String serverId) {
        XrayNodeDO row = xrayNodeMapper.selectById(serverId);
        if (ObjectUtil.isNull(row)) {
            // 复用 SERVER_STATE_NOT_FOUND 错误码 (语义一致: server 没 nook 内部状态记录)
            throw new BusinessException(XrayErrorCode.SERVER_STATE_NOT_FOUND, serverId);
        }
        return row;
    }

    @Override
    public XrayNodeDO loadOrNull(String serverId) {
        return xrayNodeMapper.selectById(serverId);
    }

    @Override
    public void markReplayDone(String serverId, LocalDateTime xrayUptime) {
        int affected = xrayNodeMapper.updateXrayUptime(serverId, xrayUptime);
        if (affected == 0) {
            log.warn("[xray-node] markReplayDone 没匹配到行 server={} (xray_node 缺失?)", serverId);
        }
    }
}
