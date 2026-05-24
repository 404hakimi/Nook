package com.nook.biz.node.service.xray.server.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.mysql.mapper.XrayServerMapper;
import com.nook.biz.node.service.xray.server.XrayServerService;
import com.nook.common.utils.collection.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Xray 实例元数据 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XrayServerServiceImpl implements XrayServerService {

    private final XrayServerMapper xrayServerMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(XrayServerDO entity) {
        XrayServerDO existing = xrayServerMapper.selectById(entity.getServerId());
        if (ObjectUtil.isNull(existing)) {
            xrayServerMapper.insert(entity);
            log.info("[xray-server] insert server={} version={} apiPort={}",
                    entity.getServerId(), entity.getXrayVersion(), entity.getXrayApiPort());
        } else {
            // 重装时 lastXrayUptime 清零, reconciler 后续重新探测
            entity.setLastXrayUptime(null);
            xrayServerMapper.updateById(entity);
            log.info("[xray-server] update server={} version={} apiPort={}",
                    entity.getServerId(), entity.getXrayVersion(), entity.getXrayApiPort());
        }
    }

    @Override
    public XrayServerDO get(String serverId) {
        return xrayServerMapper.selectById(serverId);
    }

    @Override
    public Map<String, XrayServerDO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                xrayServerMapper.selectBatchIds(serverIds), XrayServerDO::getServerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReplayDone(String serverId, LocalDateTime xrayUptime) {
        int affected = xrayServerMapper.updateXrayUptime(serverId, xrayUptime);
        if (affected == 0) {
            log.warn("[xray-server] markReplayDone 没匹配到行 server={} (xray_server 缺失?)", serverId);
        }
    }
}
