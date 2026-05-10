package com.nook.biz.node.service.xray.node;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class XrayNodeServiceImpl implements XrayNodeService {

    @Resource
    private XrayNodeMapper xrayNodeMapper;

    @Override
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
            return;
        }
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
