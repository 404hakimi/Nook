package com.nook.common.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 实体基类，统一 id + 创建/更新时间；逻辑删除字段按需在子类追加 @TableLogic。 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 32 位无连字符 UUID，由 MyBatis-Plus 在 INSERT 时生成
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    // INSERT 时由 MetaObjectHandlerImpl 填充
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // INSERT / UPDATE 时由 MetaObjectHandlerImpl 填充
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
