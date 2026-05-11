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
                       String xrayInstallDir,
                       String xrayLogDir,
                       int slotPoolSize,
                       int slotPortBase) {
        // installedAt 每次部署覆写 (语义=最近一次部署完成时间); lastXrayUptime 重装清空, 等 reconciler 重新探测.
        XrayNodeDO existing = xrayNodeMapper.selectById(serverId);
        boolean isInsert = ObjectUtil.isNull(existing);
        XrayNodeDO row = isInsert ? new XrayNodeDO() : existing;

        // 公共字段统一装载
        row.setXrayVersion(xrayVersion);
        row.setXrayApiPort(xrayApiPort);
        row.setXrayInstallDir(xrayInstallDir);
        row.setXrayLogDir(xrayLogDir);
        row.setSlotPoolSize(slotPoolSize);
        row.setSlotPortBase(slotPortBase);
        row.setInstalledAt(LocalDateTime.now());

        // 差异字段 + 落库分支
        if (isInsert) {
            row.setServerId(serverId);
            xrayNodeMapper.insert(row);
        } else {
            row.setLastXrayUptime(null);
            xrayNodeMapper.updateById(row);
        }
        log.info("[xray-node] {} server={} version={} apiPort={} installDir={} poolSize={}",
                isInsert ? "insert" : "update",
                serverId, xrayVersion, xrayApiPort, xrayInstallDir, slotPoolSize);

        // xray_node 与 slot 池在同事务初始化, 杜绝半初始化态; initialize 自身幂等.
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
