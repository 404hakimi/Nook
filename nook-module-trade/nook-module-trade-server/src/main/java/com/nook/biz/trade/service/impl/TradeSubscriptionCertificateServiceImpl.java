package com.nook.biz.trade.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.api.enums.TradeCertStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionCertificateMapper;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 订阅凭证 Service 实现类
 *
 * @author nook
 */
@Service
public class TradeSubscriptionCertificateServiceImpl implements TradeSubscriptionCertificateService {

    @Resource
    private TradeSubscriptionCertificateMapper tradeSubscriptionCertificateMapper;

    @Override
    public TradeSubscriptionCertificateDO issue(String subscriptionId, String memberUserId, String source) {
        // 先生成 id, 连接身份引用它, 故不依赖 ASSIGN_UUID 自动生成
        String certId = IdUtil.simpleUUID();
        TradeSubscriptionCertificateDO cert = new TradeSubscriptionCertificateDO();
        cert.setId(certId);
        cert.setSubscriptionId(subscriptionId);
        cert.setSource(source);
        cert.setAuthUser("member_" + memberUserId + "_" + certId);
        cert.setAuthSecret(UUID.randomUUID().toString());
        cert.setCertStatus(TradeCertStatusEnum.ACTIVE.getState());
        tradeSubscriptionCertificateMapper.insert(cert);
        return cert;
    }

    @Override
    public void setAllocation(String certId, String serverId, String ipId) {
        TradeSubscriptionCertificateDO cert = new TradeSubscriptionCertificateDO();
        cert.setId(certId);
        cert.setServerId(serverId);
        cert.setIpId(ipId);
        tradeSubscriptionCertificateMapper.updateById(cert);
    }

    @Override
    public void clearAllocation(String certId) {
        // 置 null 需显式 set (updateById 跳过 null 字段); Wrapper 更新不自动填 updated_at, 显式写
        tradeSubscriptionCertificateMapper.update(null, Wrappers.<TradeSubscriptionCertificateDO>lambdaUpdate()
                .set(TradeSubscriptionCertificateDO::getServerId, null)
                .set(TradeSubscriptionCertificateDO::getIpId, null)
                .set(TradeSubscriptionCertificateDO::getUpdatedAt, LocalDateTime.now())
                .eq(TradeSubscriptionCertificateDO::getId, certId));
    }

    @Override
    public void updateCertStatus(String certId, String certStatus) {
        TradeSubscriptionCertificateDO cert = new TradeSubscriptionCertificateDO();
        cert.setId(certId);
        cert.setCertStatus(certStatus);
        tradeSubscriptionCertificateMapper.updateById(cert);
    }

    @Override
    public void revoke(String certId) {
        // 置应移除 + 清空分配; server/ip 置 null 需显式 set, updated_at 显式写
        tradeSubscriptionCertificateMapper.update(null, Wrappers.<TradeSubscriptionCertificateDO>lambdaUpdate()
                .set(TradeSubscriptionCertificateDO::getCertStatus, TradeCertStatusEnum.REVOKED.getState())
                .set(TradeSubscriptionCertificateDO::getServerId, null)
                .set(TradeSubscriptionCertificateDO::getIpId, null)
                .set(TradeSubscriptionCertificateDO::getUpdatedAt, LocalDateTime.now())
                .eq(TradeSubscriptionCertificateDO::getId, certId));
    }

    @Override
    public TradeSubscriptionCertificateDO get(String certId) {
        return tradeSubscriptionCertificateMapper.selectById(certId);
    }

    @Override
    public List<TradeSubscriptionCertificateDO> listActiveByServer(String serverId) {
        return tradeSubscriptionCertificateMapper.selectActiveByServerId(serverId);
    }

    @Override
    public List<TradeSubscriptionCertificateDO> listBySubscription(String subscriptionId) {
        return tradeSubscriptionCertificateMapper.selectBySubscriptionId(subscriptionId);
    }

    @Override
    public List<TradeSubscriptionCertificateDO> listBySubscriptionIds(Collection<String> subscriptionIds) {
        return tradeSubscriptionCertificateMapper.selectBySubscriptionIds(subscriptionIds);
    }

    @Override
    public List<TradeSubscriptionCertificateDO> listByIds(Collection<String> certIds) {
        return tradeSubscriptionCertificateMapper.selectByIds(certIds);
    }

    @Override
    public TradeSubscriptionCertificateDO getByIpId(String ipId) {
        return tradeSubscriptionCertificateMapper.selectByIpId(ipId);
    }

    @Override
    public Set<String> filterBoundIpIds(Collection<String> ipIds) {
        return new HashSet<>(tradeSubscriptionCertificateMapper.selectBoundIpIds(ipIds));
    }
}
