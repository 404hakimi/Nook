package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订阅换机历史日志 DO (只追加, 不更新不删除)
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_subscription_change_log")
public class TradeSubscriptionChangeLogDO extends BaseEntity {

    /** 订阅 id. */
    private String subscriptionId;

    /** 会员 id. */
    private String memberUserId;

    /** 换机类型 {@link TradeSubscriptionChangeTypeEnum} */
    private String changeType;

    /** 原机器 server id; 空=初始分配. */
    private String oldServerId;

    /** 新机器 server id; 空=释放. */
    private String newServerId;

    /** 变更原因 {@link TradeSubscriptionChangeReasonEnum} */
    private String reason;

    /** 操作者 admin id; 系统触发为 system. */
    private String operator;
}
