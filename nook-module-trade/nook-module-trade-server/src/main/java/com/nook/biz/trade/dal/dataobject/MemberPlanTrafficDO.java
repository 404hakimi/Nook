package com.nook.biz.trade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅流量计量 DO (member_plan_traffic).
 *
 * <p>按订阅记一份用量: 源是该订阅独占落地机的 NIC tx (1:1 ⟹ 机器流量=该用户流量).
 * {@code usedBytes} 是 DB 强一致权威, 机器侧 (vnstat/VPS) 怎么崩都不动它; {@code lastCounterTx}
 * 只是后端算"这次增量"的游标. 累加规则见 {@code TradeLifecycleJob}: 换落地机重基线 / 计数回退当重置 / 正常累加.
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

    /** 上次采样落地机 tx 计数 (增量游标; 落地机 NIC 当周期 tx, 月度会归零, 由"回退即重置"兜). */
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
