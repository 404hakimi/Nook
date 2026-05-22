package com.nook.biz.node.service.xray.node;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.xray.vo.XrayNodePageReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Xray 节点 Service 实现.
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XrayNodeServiceImpl implements XrayNodeService {

    private final XrayNodeMapper xrayNodeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsertXrayNode(String serverId, String xrayVersion, int xrayApiPort,
                               String xrayInstallDir,
                               String xrayBinaryPath, String xrayConfigPath, String xrayShareDir,
                               String xrayLogDir,
                               int touchdownSize,
                               String protocol, String transport, String listenIp,
                               int sharedInboundPort, String wsPath, String domain,
                               String tlsCertPath, String tlsKeyPath) {

        // installedAt 每次部署覆写; lastXrayUptime 重装清空, 等 reconciler 重新探测
        XrayNodeDO existing = xrayNodeMapper.selectById(serverId);
        boolean isInsert = ObjectUtil.isNull(existing);
        XrayNodeDO row = isInsert ? new XrayNodeDO() : existing;

        row.setXrayVersion(xrayVersion);
        row.setXrayApiPort(xrayApiPort);
        row.setXrayInstallDir(xrayInstallDir);
        row.setXrayBinaryPath(xrayBinaryPath);
        row.setXrayConfigPath(xrayConfigPath);
        row.setXrayShareDir(xrayShareDir);
        row.setXrayLogDir(xrayLogDir);
        row.setTouchdownSize(touchdownSize);
        row.setProtocol(protocol);
        row.setTransport(transport);
        row.setListenIp(listenIp);
        row.setSharedInboundPort(sharedInboundPort);
        row.setWsPath(wsPath);
        row.setDomain(domain);
        row.setTlsCertPath(tlsCertPath);
        row.setTlsKeyPath(tlsKeyPath);
        row.setInstalledAt(LocalDateTime.now());

        if (isInsert) {
            row.setServerId(serverId);
            xrayNodeMapper.insert(row);
        } else {
            row.setLastXrayUptime(null);
            xrayNodeMapper.updateById(row);
        }
        log.info("[xray-node] {} server={} version={} apiPort={} installDir={} touchdownSize={}",
                isInsert ? "insert" : "update",
                serverId, xrayVersion, xrayApiPort, xrayInstallDir, touchdownSize);
    }

    @Override
    public XrayNodeDO getXrayNode(String serverId) {
        return xrayNodeMapper.selectById(serverId);
    }

    @Override
    public Map<String, XrayNodeDO> getXrayNodeMap(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                xrayNodeMapper.selectBatchIds(serverIds), XrayNodeDO::getServerId);
    }

    @Override
    public void markReplayDone(String serverId, LocalDateTime xrayUptime) {
        int affected = xrayNodeMapper.updateXrayUptime(serverId, xrayUptime);
        if (affected == 0) {
            log.warn("[xray-node] markReplayDone 没匹配到行 server={} (xray_node 缺失?)", serverId);
        }
    }

    @Override
    public PageResult<XrayNodeDO> getXrayNodePage(XrayNodePageReqVO pageReqVO) {
        IPage<XrayNodeDO> result = xrayNodeMapper.selectPageByQuery(
                Page.of(pageReqVO.getPageNo(), pageReqVO.getPageSize()), pageReqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }
}
