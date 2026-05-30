package com.nook.biz.trade.convert;

import com.nook.biz.trade.controller.vo.TradeSubscriptionRespVO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Map;

@Mapper
public interface TradeSubscriptionConvert {

    TradeSubscriptionConvert INSTANCE = Mappers.getMapper(TradeSubscriptionConvert.class);

    @Mapping(target = "planName", source = "planName")
    @Mapping(target = "memberEmail", source = "memberEmail")
    TradeSubscriptionRespVO toRespVO(TradeSubscriptionDO sub, String planName, String memberEmail);

    default PageResult<TradeSubscriptionRespVO> convertPage(PageResult<TradeSubscriptionDO> page,
                                                            Map<String, String> planNameMap,
                                                            Map<String, String> memberEmailMap) {
        return PageResult.of(page.getTotal(),
                CollectionUtils.convertList(page.getRecords(), s -> toRespVO(
                        s, planNameMap.get(s.getPlanId()), memberEmailMap.get(s.getMemberUserId()))));
    }
}
