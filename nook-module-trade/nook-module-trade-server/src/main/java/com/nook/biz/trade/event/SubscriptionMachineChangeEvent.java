package com.nook.biz.trade.event;

import com.nook.biz.trade.api.enums.TradeSubscriptionChangeReasonEnum;
import com.nook.biz.trade.api.enums.TradeSubscriptionChangeTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 订阅换机事件; 开通分配 / 故障切换 / 客户换IP 等机器绑定变更后发布, 监听器落历史日志.
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class SubscriptionMachineChangeEvent {

    /** 订阅 id. */
    private String subscriptionId;

    /** 会员 id. */
    private String memberUserId;

    /** 换机类型 {@link TradeSubscriptionChangeTypeEnum} */
    private TradeSubscriptionChangeTypeEnum changeType;

    /** 原机器 server id; 空=初始分配. */
    private String oldServerId;

    /** 新机器 server id; 空=释放. */
    private String newServerId;

    /** 变更原因 {@link TradeSubscriptionChangeReasonEnum} */
    private TradeSubscriptionChangeReasonEnum reason;

    /** 操作者 admin id; 系统触发为 system. */
    private String operator;
}
