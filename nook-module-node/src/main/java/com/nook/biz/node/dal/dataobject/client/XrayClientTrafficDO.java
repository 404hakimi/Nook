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
 * Xray 用户流量累计快照 (xray_client_traffic).
 *
 * <p>xray 的 stats counter 是 in-memory, 进程重启 / inbound 重建都会清零;
 * 本表用定时 {@code statsquery --reset} 把增量周期性累加进来, 是流量计费/上限判断的唯一持久来源.
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

    /** 累计上行字节; 跨 xray 重启不丢 */
    private Long uplinkBytes;

    /** 累计下行字节; 跨 xray 重启不丢 */
    private Long downlinkBytes;

    private LocalDateTime lastSampledAt;
}
