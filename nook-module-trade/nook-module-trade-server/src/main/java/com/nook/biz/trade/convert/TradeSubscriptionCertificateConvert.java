package com.nook.biz.trade.convert;

import com.nook.biz.trade.api.dto.SubscriptionCertRespDTO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 订阅凭证 Convert
 *
 * @author nook
 */
@Mapper
public interface TradeSubscriptionCertificateConvert {

    TradeSubscriptionCertificateConvert INSTANCE = Mappers.getMapper(TradeSubscriptionCertificateConvert.class);

    SubscriptionCertRespDTO toRespDTO(TradeSubscriptionCertificateDO bean);
}
