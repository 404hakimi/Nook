package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.trade.api.enums.TradeCertSourceEnum;
import com.nook.biz.trade.api.enums.TradeCertStatusEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订阅凭证 DO; 每个接入点一条, 含连接验证信息 + 分配 + 期望态
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_subscription_certificate")
public class TradeSubscriptionCertificateDO extends BaseEntity {

    /** 所属订阅. */
    private String subscriptionId;

    /** 来源 {@link TradeCertSourceEnum} */
    private String source;

    /** 分配的线路机; 故障切换改此, 未分配为空. */
    private String serverId;

    /** 分配的落地机; 已释放为空. */
    private String ipId;

    /** 连接身份; agent 按协议渲染成 xray email 等. */
    private String authUser;

    /** 连接密钥; agent 按协议渲染成 uuid / password. */
    private String authSecret;

    /** 期望态 {@link TradeCertStatusEnum} */
    private String certStatus;
}
