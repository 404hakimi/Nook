package com.nook.biz.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 区域字典 DO
 *
 * @author nook
 */
@Data
@TableName("system_region")
public class SystemRegionDO {

    /** 区域码: JP-TYO / US-LAX / HK / SG 等. */
    @TableId(type = IdType.INPUT)
    private String code;

    /** ISO 3166-1 alpha-2 国家码. */
    private String countryCode;

    /** 国家名. */
    private String countryName;

    /** 城市 (可空). */
    private String city;

    /** 展示名, 如 "日本 · 东京". */
    private String displayName;

    /** 国旗 emoji. */
    private String flagEmoji;

    /** 1=启用 0=停用. */
    private Integer enabled;

    /** 创建时间. */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间. */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
