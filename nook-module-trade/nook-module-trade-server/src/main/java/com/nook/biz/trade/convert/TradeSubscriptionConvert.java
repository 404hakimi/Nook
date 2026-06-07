package com.nook.biz.trade.convert;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.trade.controller.vo.TradeSubscriptionRespVO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
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

    // trafficGb/usedBytes/线路机/落地机 由 convertPage 按行补全, 单行场景留空
    @Mapping(target = "trafficGb", ignore = true)
    @Mapping(target = "usedBytes", ignore = true)
    @Mapping(target = "frontlineServerId", ignore = true)
    @Mapping(target = "frontlineIp", ignore = true)
    @Mapping(target = "landingServerId", ignore = true)
    @Mapping(target = "landingIp", ignore = true)
    TradeSubscriptionRespVO toRespVO(TradeSubscriptionDO sub, String planName, String memberEmail);

    default PageResult<TradeSubscriptionRespVO> convertPage(PageResult<TradeSubscriptionDO> page,
                                                            Map<String, TradePlanDO> planMap,
                                                            Map<String, String> memberEmailMap,
                                                            Map<String, Long> usedBytesBySub,
                                                            Map<String, String> subFrontlineMap,
                                                            Map<String, String> subLandingMap,
                                                            Map<String, String> serverIpMap) {
        return PageResult.of(page.getTotal(), CollectionUtils.convertList(page.getRecords(), sub -> {
            TradePlanDO plan = planMap.get(sub.getPlanId());
            TradeSubscriptionRespVO vo = toRespVO(sub,
                    ObjectUtil.isNull(plan) ? null : plan.getName(),
                    memberEmailMap.get(sub.getMemberUserId()));
            vo.setTrafficGb(ObjectUtil.isNull(plan) ? null : plan.getTrafficGb());
            // 本周期已用 = 名下各接入点当周期 traffic 行 used_bytes 之和
            vo.setUsedBytes(usedBytesBySub.getOrDefault(sub.getId(), 0L));
            String frontlineServerId = subFrontlineMap.get(sub.getId());
            vo.setFrontlineServerId(frontlineServerId);
            vo.setFrontlineIp(ObjectUtil.isNull(frontlineServerId) ? null : serverIpMap.get(frontlineServerId));
            String landingServerId = subLandingMap.get(sub.getId());
            vo.setLandingServerId(landingServerId);
            vo.setLandingIp(ObjectUtil.isNull(landingServerId) ? null : serverIpMap.get(landingServerId));
            return vo;
        }));
    }
}
