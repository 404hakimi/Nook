package com.nook.biz.node.service.xray.server.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nook.biz.node.api.enums.XrayInstallStatusEnum;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.mapper.XrayInstallMapper;
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
    @Transactional(rollbackFor = Exception.class)
    public void markReplayDone(String serverId, LocalDateTime xrayUptime) {
        int affected = xrayInstallMapper.updateXrayUptime(serverId, xrayUptime);
        if (affected == 0) {
            log.warn("[xray-install] markReplayDone 没匹配到行 server={} (xray_install 缺失?)", serverId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markInstallStatus(String serverId, XrayInstallStatusEnum status) {
        // 定向更新 install_status (OK 时同步置 installedAt), 避免 updateById 把其它列覆成 null
        int affected = xrayInstallMapper.update(null, new LambdaUpdateWrapper<XrayInstallDO>()
                .eq(XrayInstallDO::getServerId, serverId)
                .set(XrayInstallDO::getInstallStatus, status.getCode())
                .set(status == XrayInstallStatusEnum.OK, XrayInstallDO::getInstalledAt, LocalDateTime.now()));
        if (affected == 0) {
            log.warn("[xray-install] markInstallStatus 没匹配到行 server={} status={}", serverId, status.getCode());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTlsCert(String serverId, String certPem, String keyPem, LocalDateTime notAfter) {
        int affected = xrayInstallMapper.update(null, new LambdaUpdateWrapper<XrayInstallDO>()
                .eq(XrayInstallDO::getServerId, serverId)
                .set(XrayInstallDO::getTlsCertPem, certPem)
                .set(XrayInstallDO::getTlsKeyPem, keyPem)
                .set(XrayInstallDO::getTlsCertNotAfter, notAfter));
        if (affected == 0) {
            log.warn("[xray-install] saveTlsCert 没匹配到行 server={}", serverId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearTlsBinding(String serverId) {
        // 显式 set null (wrapper.set 不受全局 NOT_NULL 策略约束, 区别于 updateById)
        xrayInstallMapper.update(null, new LambdaUpdateWrapper<XrayInstallDO>()
                .eq(XrayInstallDO::getServerId, serverId)
                .set(XrayInstallDO::getDomainId, null)
                .set(XrayInstallDO::getSubdomain, null)
                .set(XrayInstallDO::getTlsCertPem, null)
                .set(XrayInstallDO::getTlsKeyPem, null)
                .set(XrayInstallDO::getTlsCertNotAfter, null));
    }
}
