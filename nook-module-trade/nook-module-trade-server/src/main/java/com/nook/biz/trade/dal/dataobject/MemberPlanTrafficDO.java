package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅流量计量 DO
 *
 * @author nook
 */
@Data
@TableName("member_plan_traffic")
public class MemberPlanTrafficDO {

    /** 订阅 id (主键; 一份订阅一行). */
    @TableId(type = IdType.INPUT)
    private String subscriptionId;

    /** 会员 id (冗余, 按会员查). */
    private String memberUserId;

    /** 当前绑定的落地机 server id; 换机时更新并重打基线. */
    private String landingServerId;

    /** 本周期累计已用字节 (DB 权威, 机器侧事故不动). */
    private Long usedBytes;

    /** 上次采样到的落地机累计流量 (增量计量游标; 优先业务流量, 老机回退整机出站; 计数回退视为重置, 重新打基线). */
    private Long lastCounterTx;

    /** 周期重置时间; null = 窗口为订阅生命周期, 不中途重置 (续费=新订阅=新行). */
    private LocalDateTime cycleResetAt;

    /** 上次采样时间. */
    private LocalDateTime lastSampledAt;

    /** 创建时间. */
    private LocalDateTime createdAt;

    /** 更新时间. */
    private LocalDateTime updatedAt;
}
