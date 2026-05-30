package com.nook.biz.trade.convert;

import com.nook.biz.trade.controller.vo.TradeSubscriptionChangeLogRespVO;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionChangeLogDO;
import com.nook.common.utils.collection.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

@Mapper
public interface TradeSubscriptionChangeLogConvert {

    TradeSubscriptionChangeLogConvert INSTANCE = Mappers.getMapper(TradeSubscriptionChangeLogConvert.class);

    // memberEmail/oldServerIp/newServerIp 由 convertList 按行补全
    @Mapping(target = "memberEmail", ignore = true)
    @Mapping(target = "oldServerIp", ignore = true)
    @Mapping(target = "newServerIp", ignore = true)
    TradeSubscriptionChangeLogRespVO toRespVO(TradeSubscriptionChangeLogDO changeLog);

    default List<TradeSubscriptionChangeLogRespVO> convertList(List<TradeSubscriptionChangeLogDO> logs,
                                                               Map<String, String> serverIpMap,
                                                               Map<String, String> memberEmailMap) {
        return CollectionUtils.convertList(logs, changeLog -> {
            TradeSubscriptionChangeLogRespVO vo = toRespVO(changeLog);
            vo.setMemberEmail(memberEmailMap.get(changeLog.getMemberUserId()));
            vo.setOldServerIp(changeLog.getOldServerId() == null ? null : serverIpMap.get(changeLog.getOldServerId()));
            vo.setNewServerIp(changeLog.getNewServerId() == null ? null : serverIpMap.get(changeLog.getNewServerId()));
            return vo;
        });
    }
}
