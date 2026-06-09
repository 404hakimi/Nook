package com.nook.biz.node.service.xray.server.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nook.biz.node.dal.dataobject.node.XrayInstallDO;
import com.nook.biz.node.dal.mysql.mapper.XrayInstallMapper;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * Xray 实例元数据 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayInstallServiceImpl implements XrayInstallService {

    @Resource
    private XrayInstallMapper xrayInstallMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(XrayInstallDO entity) {
        XrayInstallDO existing = xrayInstallMapper.selectById(entity.getServerId());
        if (ObjectUtil.isNull(existing)) {
            xrayInstallMapper.insert(entity);
            log.info("[xray-install] insert server={} version={} apiPort={}",
                    entity.getServerId(), entity.getXrayVersion(), entity.getXrayApiPort());
        } else {
            // 重装时清零上次启动时间, 由对账任务后续重新探测
            entity.setLastXrayUptime(null);
            xrayInstallMapper.updateById(entity);
            log.info("[xray-install] update server={} version={} apiPort={}",
                    entity.getServerId(), entity.getXrayVersion(), entity.getXrayApiPort());
        }
    }

    @Override
    public XrayInstallDO get(String serverId) {
        return xrayInstallMapper.selectById(serverId);
    }

    @Override
    public boolean isSubdomainTaken(String domainId, String subdomain, String excludeServerId) {
        return xrayInstallMapper.selectCount(new LambdaQueryWrapper<XrayInstallDO>()
                .eq(XrayInstallDO::getDomainId, domainId)
                .eq(XrayInstallDO::getSubdomain, subdomain)
                .ne(XrayInstallDO::getServerId, excludeServerId)) > 0;
    }

    @Override
    public Map<String, XrayInstallDO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                xrayInstallMapper.selectBatchIds(serverIds), XrayInstallDO::getServerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReplayDone(String serverId, LocalDateTime xrayUptime) {
        int affected = xrayInstallMapper.updateXrayUptime(serverId, xrayUptime);
        if (affected == 0) {
            log.warn("[xray-install] markReplayDone 没匹配到行 server={} (xray_install 缺失?)", serverId);
        }
    }
}
