package com.nook.biz.trade.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.trade.api.enums.TradeCertStatusEnum;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;
import cn.hutool.core.collection.CollUtil;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 订阅凭证 Mapper
 *
 * @author nook
 */
@Mapper
public interface TradeSubscriptionCertificateMapper extends BaseMapper<TradeSubscriptionCertificateDO> {

    /** 某订阅的全部凭证. */
    default List<TradeSubscriptionCertificateDO> selectBySubscriptionId(String subscriptionId) {
        return selectList(Wrappers.<TradeSubscriptionCertificateDO>lambdaQuery()
                .eq(TradeSubscriptionCertificateDO::getSubscriptionId, subscriptionId));
    }

    /** 某线路机上应运行的凭证 (agent 对账拉取用). */
    default List<TradeSubscriptionCertificateDO> selectActiveByServerId(String serverId) {
        return selectList(Wrappers.<TradeSubscriptionCertificateDO>lambdaQuery()
                .eq(TradeSubscriptionCertificateDO::getServerId, serverId)
                .eq(TradeSubscriptionCertificateDO::getCertStatus, TradeCertStatusEnum.ACTIVE.getState()));
    }

    /** 按落地机查凭证 (落地机与凭证 1:1; 无返 null). */
    default TradeSubscriptionCertificateDO selectByIpId(String ipId) {
        return selectOne(Wrappers.<TradeSubscriptionCertificateDO>lambdaQuery()
                .eq(TradeSubscriptionCertificateDO::getIpId, ipId)
                .last("LIMIT 1"));
    }

    /** 批量查凭证. */
    default List<TradeSubscriptionCertificateDO> selectByIds(Collection<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        return selectBatchIds(ids);
    }
}
