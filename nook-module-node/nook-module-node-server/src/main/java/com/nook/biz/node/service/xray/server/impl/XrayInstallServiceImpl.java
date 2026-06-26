package com.nook.biz.node.service.xray.server.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.XrayInstallStatusEnum;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.mapper.XrayInstallMapper;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return xrayInstallMapper.existsBySubdomain(domainId, subdomain, excludeServerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInstallStatus(String serverId, XrayInstallStatusEnum status) {
        // 定向更新 install_status (OK 时同步置 installedAt), 避免 updateById 把其它列覆成 null
        int affected = xrayInstallMapper.updateInstallStatus(
                serverId, status.getCode(), status == XrayInstallStatusEnum.OK);
        if (affected == 0) {
            log.warn("[xray-install] markInstallStatus 没匹配到行 server={} status={}", serverId, status.getCode());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearTlsBinding(String serverId) {
        // 证书清理走 XrayTlsCertService.clear; 此处只清域名绑定 (mapper 内显式 set null, 不受全局 NOT_NULL 策略约束)
        xrayInstallMapper.clearTlsBinding(serverId);
    }
}
