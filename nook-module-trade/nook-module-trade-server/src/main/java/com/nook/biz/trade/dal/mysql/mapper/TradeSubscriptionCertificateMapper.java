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

    /** 批量查多订阅的凭证. */
    default List<TradeSubscriptionCertificateDO> selectBySubscriptionIds(Collection<String> subscriptionIds) {
        if (CollUtil.isEmpty(subscriptionIds)) {
            return List.of();
        }
        return selectList(Wrappers.<TradeSubscriptionCertificateDO>lambdaQuery()
                .in(TradeSubscriptionCertificateDO::getSubscriptionId, subscriptionIds));
    }

    /** 主线路机为该机的应运行凭证 (主口径: 备机不算). */
    default List<TradeSubscriptionCertificateDO> selectActiveByServerId(String serverId) {
        return selectList(Wrappers.<TradeSubscriptionCertificateDO>lambdaQuery()
                .eq(TradeSubscriptionCertificateDO::getServerId, serverId)
                .eq(TradeSubscriptionCertificateDO::getCertStatus, TradeCertStatusEnum.ACTIVE.getState()));
    }

    /** 候选组含该机的应运行凭证 (组口径: 主备都算). */
    default List<TradeSubscriptionCertificateDO> selectActiveByServerIdInGroup(String serverId) {
        return selectList(Wrappers.<TradeSubscriptionCertificateDO>lambdaQuery()
                // 备机存 CSV, FIND_IN_SET 按成员精确匹配
                .and(q -> q.eq(TradeSubscriptionCertificateDO::getServerId, serverId)
                        .or().apply("FIND_IN_SET({0}, standby_server_ids)", serverId))
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

    /** 这批落地机里已被占用的 (ip_id 非空即占用, 含 ACTIVE/SUSPENDED; REVOKED 已清空 ip_id). */
    default List<String> selectBoundIpIds(Collection<String> ipIds) {
        if (CollUtil.isEmpty(ipIds)) {
            return List.of();
        }
        return selectList(Wrappers.<TradeSubscriptionCertificateDO>lambdaQuery()
                        .select(TradeSubscriptionCertificateDO::getIpId)
                        .in(TradeSubscriptionCertificateDO::getIpId, ipIds))
                .stream().map(TradeSubscriptionCertificateDO::getIpId).toList();
    }
}
