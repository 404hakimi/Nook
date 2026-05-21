package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 区域字典; code 即主键 (JP-TYO / US-LAX / HK 等).
 * 用作 resource_server.region / resource_ip_pool.region 的 FK.
 *
 * @author nook
 */
@Data
@TableName("resource_region")
public class ResourceRegionDO {

    /** 区域码: JP-TYO / US-LAX / HK / SG 等. */
    @TableId
    private String code;

    /** ISO 3166-1 alpha-2. */
    private String countryCode;

    private String countryName;

    /** 城市 (可空). */
    private String city;

    /** 展示名, 如 "日本 · 东京". */
    private String displayName;

    /** 国旗 emoji. */
    private String flagEmoji;

    /** 1=启用 0=停用. */
    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
