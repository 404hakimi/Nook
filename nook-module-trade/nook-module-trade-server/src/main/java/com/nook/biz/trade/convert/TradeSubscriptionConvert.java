package com.nook.biz.trade.convert;

import com.nook.biz.trade.controller.vo.TradeSubscriptionRespVO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionDO;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订阅 VO 转换; 纯转换, 不依赖 Api / Service.
 *
 * <p>planName 不在 DO 上, 由 controller 从 service 取后传入.
 *
 * @author nook
 */
@Mapper
public interface TradeSubscriptionConvert {

    TradeSubscriptionConvert INSTANCE = Mappers.getMapper(TradeSubscriptionConvert.class);

    /** planName / memberEmail 由带 map 的重载补; 单独调本方法时留 null. */
    @Mapping(target = "planName", ignore = true)
    @Mapping(target = "memberEmail", ignore = true)
    TradeSubscriptionRespVO toRespVO(TradeSubscriptionDO sub);

    default TradeSubscriptionRespVO toRespVO(TradeSubscriptionDO sub, String planName) {
        TradeSubscriptionRespVO vo = toRespVO(sub);
        vo.setPlanName(planName);
        return vo;
    }

    default PageResult<TradeSubscriptionRespVO> convertPage(PageResult<TradeSubscriptionDO> page,
                                                            Map<String, String> planNameMap,
                                                            Map<String, String> memberEmailMap) {
        List<TradeSubscriptionRespVO> records = page.getRecords().stream()
                .map(s -> {
                    TradeSubscriptionRespVO vo = toRespVO(s, planNameMap.get(s.getPlanId()));
                    vo.setMemberEmail(memberEmailMap.get(s.getMemberUserId()));
                    return vo;
                })
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), records);
    }
}
