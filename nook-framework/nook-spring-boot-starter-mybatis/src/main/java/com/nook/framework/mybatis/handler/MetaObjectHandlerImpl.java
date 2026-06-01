package com.nook.framework.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 *
 * @author nook
 */
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

    /** UPDATE 时强制刷新 updatedAt (无条件覆盖; strictUpdateFill 只填 null 值, 对 updateById 已加载实体不生效). */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
    }
}
