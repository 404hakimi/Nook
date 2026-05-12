package com.nook.biz.operation.service;

import com.nook.biz.operation.dal.dataobject.OpConfigDO;

import java.util.List;

/**
 * Op 配置 Service 接口
 *
 * @author nook
 */
public interface OpConfigService {

    List<OpConfigDO> getOpConfigList();

    OpConfigDO getOpConfig(String id);

    OpConfigDO getOpConfigByType(String opType);

    /** 更新 Op 配置; 写后发布 {@link com.nook.biz.operation.api.event.OpConfigChangedEvent} */
    void updateOpConfig(String id,
                        Integer execTimeoutSeconds,
                        Integer waitTimeoutSeconds,
                        Integer maxRetry,
                        Boolean enabled,
                        String description);

    /** 仅插入缺失行 (Bootstrapper 启动期用), 已存在的不动; 返回是否真插入 */
    boolean insertIfAbsent(OpConfigDO opConfig);

    /** 重置到 yml 默认值 */
    void resetOpConfig(String id);
}
