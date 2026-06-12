package com.nook.biz.node.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 服务器流量计量 DO; 每服务器·每周期一行, 当周期那行在写(end_time 空), 旧行即历史.
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_server_traffic")
public class ResourceServerTrafficDO extends BaseEntity {

    /** 所属服务器. */
    private String serverId;

    /** 周期起点(我方重置日). */
    private LocalDate startTime;

    /** 周期终点; 空 = 当前在写那行. */
    private LocalDate endTime;

    /** 本周期入站累计字节(增量累加). */
    private Long rxBytes;

    /** 本周期出站累计字节(增量累加). */
    private Long txBytes;

    /** 本周期机器已用 = 入站 + 出站. */
    private Long usedBytes;

    /** 游标: 上次处理到的网卡入站累计值(翻篇带到新行). */
    private Long lastCounterRxBytes;

    /** 游标: 上次处理到的网卡出站累计值. */
    private Long lastCounterTxBytes;

    /** 落地机测到的用户上行最新累计值(覆盖, 跨周期不清零; 给 trade 差分). */
    private Long counterUpBytes;

    /** 落地机测到的用户下行最新累计值(覆盖, 跨周期不清零). */
    private Long counterDownBytes;

    /** 限流状态 {@link ResourceServerThrottleStateEnum} */
    private String throttleState;

    /** 最近上报时刻. */
    private LocalDateTime lastSampledAt;
}
