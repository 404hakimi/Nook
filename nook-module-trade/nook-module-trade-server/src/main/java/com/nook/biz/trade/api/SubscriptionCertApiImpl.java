package com.nook.biz.trade.api;

import com.nook.biz.trade.api.dto.SubscriptionCertRespDTO;
import com.nook.biz.trade.convert.TradeSubscriptionCertificateConvert;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 订阅凭证跨模块 Api 实现类
 *
 * @author nook
 */
@Service
public class SubscriptionCertApiImpl implements SubscriptionCertApi {

    @Resource
    private TradeSubscriptionCertificateService tradeSubscriptionCertificateService;

    @Override
    public List<SubscriptionCertRespDTO> listActiveByServerInGroup(String serverId) {
        return CollectionUtils.convertList(
                tradeSubscriptionCertificateService.listActiveByServerInGroup(serverId),
                TradeSubscriptionCertificateConvert.INSTANCE::toRespDTO);
    }

    @Override
    public SubscriptionCertRespDTO getById(String certId) {
        return TradeSubscriptionCertificateConvert.INSTANCE.toRespDTO(tradeSubscriptionCertificateService.get(certId));
    }

    @Override
    public SubscriptionCertRespDTO getByIp(String ipId) {
        return TradeSubscriptionCertificateConvert.INSTANCE.toRespDTO(
                tradeSubscriptionCertificateService.getByIpId(ipId));
    }

    @Override
    public List<SubscriptionCertRespDTO> listByIds(Collection<String> certIds) {
        return CollectionUtils.convertList(
                tradeSubscriptionCertificateService.listByIds(certIds),
                TradeSubscriptionCertificateConvert.INSTANCE::toRespDTO);
    }

    @Override
    public void setAllocation(String certId, String serverId, String ipId) {
        tradeSubscriptionCertificateService.setAllocation(certId, serverId, ipId);
    }

    @Override
    public void clearAllocation(String certId) {
        tradeSubscriptionCertificateService.clearAllocation(certId);
    }

    @Override
    public Set<String> filterBoundIpIds(Collection<String> ipIds) {
        return tradeSubscriptionCertificateService.filterBoundIpIds(ipIds);
    }
}
