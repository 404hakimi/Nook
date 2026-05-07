package com.nook.framework.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 自动填充 BaseEntity 的 createdAt / updatedAt。 */
@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    /** INSERT 时填充 createdAt + updatedAt。 */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 同一时刻取一次，保证两个字段值完全一致便于比对
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    /** UPDATE 时只填 updatedAt。 */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
