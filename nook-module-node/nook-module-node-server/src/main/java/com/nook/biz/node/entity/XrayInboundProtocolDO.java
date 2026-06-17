package com.nook.biz.node.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 入站协议模板 DO
 *
 * @author nook
 */
@Data
@TableName("xray_inbound_protocol")
public class XrayInboundProtocolDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 协议形态标识 (主键). */
    @TableId(value = "protocol_key", type = IdType.INPUT)
    private String protocolKey;

    /** 展示名. */
    private String name;

    /** 装机是否生成 x25519 密钥. */
    private Integer needsRealityKey;

    /** 带 ${} 占位符的 inbound JSON 模板. */
    private String inboundTemplate;

    /** 是否启用. */
    private Integer enabled;

    /** 前端排序. */
    private Integer sort;

    /** 创建时间. */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间. */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
