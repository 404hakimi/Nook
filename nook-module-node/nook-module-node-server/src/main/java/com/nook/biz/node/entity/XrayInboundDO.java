package com.nook.biz.node.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Xray inbound 共享配置 DO (业务可热改, 跟 resource_server 1:1)
 *
 * @author nook
 */
@Data
@TableName("xray_inbound")
public class XrayInboundDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 服务器 id (主键). */
    @TableId(value = "server_id", type = IdType.INPUT)
    private String serverId;

    /** 协议形态 key {@link XrayInboundProtocolEnum}; protocol/transport/security 由它解出. */
    private String protocolKey;

    /** 共享 inbound 监听端口. */
    private Integer sharedInboundPort;

    /** 协议/传输/安全细节语义参数 (JSON, 含 ws/tls/reality/flow + 对外域名); 反序列化见 InboundParams. */
    private String params;

    /** 创建时间. */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间. */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
