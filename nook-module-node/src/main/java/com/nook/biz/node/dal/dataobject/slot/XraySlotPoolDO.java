package com.nook.biz.node.dal.dataobject.slot;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Xray 1:1 slot 池条目; 复合主键 (serverId, slotIndex), 不继承 BaseEntity.
 *
 * @author nook
 */
@Data
@TableName("xray_slot_pool")
public class XraySlotPoolDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联 resource_server.id, 复合主键之一. */
    private String serverId;

    /** slot 编号 1..slot_pool_size, 复合主键之一; 实际监听端口 = slot_port_base + slot_index. */
    private Integer slotIndex;

    /** 是否被占用: 0=空闲 1=已占用. */
    private Integer used;

    /** 占用此 slot 的 xray_client.id; used=0 时为 NULL. */
    private String usedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
