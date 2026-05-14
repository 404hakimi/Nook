package com.nook.biz.node.dal.dataobject.client;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Xray 客户端流量累计表 (xray_client_traffic).
 *
 * <p>xray 进程里的计数器是内存值, 进程重启 / inbound 重建都会归零; 本表用定时 sample 把当前累计
 * 拉下来落库, 是流量计费 / 上限判断的唯一持久来源. 实际增量由 DB 端的"当前值 - 上次值"算出来,
 * xray 进程重启过 (当前值小于上次值) 时直接把当前值当增量.
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xray_client_traffic")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class XrayClientTrafficDO extends BaseEntity {

    private String clientId;

    /** 冗余 server_id, 加速按 server 批量查 / 删 */
    private String serverId;

    /** 累计上行字节 (自创建 / 上次重置以来); 跨 xray 重启不丢 */
    private Long uplinkBytes;

    /** 累计下行字节 (自创建 / 上次重置以来); 跨 xray 重启不丢 */
    private Long downlinkBytes;

    /** 上次采样时从 xray 拿到的当前累计值 (上行); 下次采样算"这次增量 = 当前值 - 上次值"时用 */
    private Long lastCounterUplink;

    /** 上次采样时从 xray 拿到的当前累计值 (下行); 同 lastCounterUplink */
    private Long lastCounterDownlink;

    private LocalDateTime lastSampledAt;
}
