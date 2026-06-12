package com.nook.biz.node.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceServerQuotaResetPolicyEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器额度/上限 DO; admin 设的纯上限, 跟服务器 1:1, 不放运行统计.
 *
 * @author nook
 */
@Data
@TableName("resource_server_quota")
public class ResourceServerQuotaDO {

    /** 服务器 id (主键). */
    @TableId
    private String serverId;

    /** 总流量配额 GB; 照抄厂商面板原值(单向计费厂商 ×2), 0/null=不限. */
    private Integer totalGb;

    /** 月配额实际可用比例%; 限流阈值 = total_gb × usable_percent/100. */
    private Integer usablePercent;

    /** 出站带宽上限 Mbps. 落地机真实限速(取 min(套餐,本值), 0=不限); 线路机供分配不超卖, 0/空=不参与分配. */
    private Integer bandwidthMbps;

    /** 按月流量重置日 1-28; 仅按月生效, 固定不重置时忽略; 取不到时按 1 号. */
    private Integer resetDay;

    /** 重置策略 {@link ResourceServerQuotaResetPolicyEnum} */
    private String resetPolicy;

    /** 创建时间. */
    private LocalDateTime createdAt;

    /** 更新时间. */
    private LocalDateTime updatedAt;
}
