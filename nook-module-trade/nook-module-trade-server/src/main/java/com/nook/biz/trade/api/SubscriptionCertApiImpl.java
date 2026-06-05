package com.nook.biz.trade.api;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.trade.api.dto.SubscriptionCertRespDTO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import com.nook.biz.trade.service.TradeSubscriptionCertificateService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 订阅凭证跨模块契约 Service 实现类
 *
 * @author nook
 */
@Service
public class SubscriptionCertApiImpl implements SubscriptionCertApi {

    @Resource
    private TradeSubscriptionCertificateService tradeSubscriptionCertificateService;

    @Override
    public List<SubscriptionCertRespDTO> listActiveByServer(String serverId) {
        return CollectionUtils.convertList(tradeSubscriptionCertificateService.listActiveByServer(serverId), this::toDto);
    }

    @Override
    public SubscriptionCertRespDTO getById(String certId) {
        return this.toDto(tradeSubscriptionCertificateService.get(certId));
    }

    @Override
    public SubscriptionCertRespDTO getByIp(String ipId) {
        return this.toDto(tradeSubscriptionCertificateService.getByIpId(ipId));
    }

    @Override
    public List<SubscriptionCertRespDTO> listByIds(Collection<String> certIds) {
        return CollectionUtils.convertList(tradeSubscriptionCertificateService.listByIds(certIds), this::toDto);
    }

    @Override
    public void setAllocation(String certId, String serverId, String ipId) {
        tradeSubscriptionCertificateService.setAllocation(certId, serverId, ipId);
    }

    @Override
    public void clearAllocation(String certId) {
        tradeSubscriptionCertificateService.clearAllocation(certId);
    }

    private SubscriptionCertRespDTO toDto(TradeSubscriptionCertificateDO cert) {
        if (ObjectUtil.isNull(cert)) {
            return null;
        }
        return BeanUtils.toBean(cert, SubscriptionCertRespDTO.class);
    }
}
