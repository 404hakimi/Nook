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

    /** 新建 Op 配置; opType + name 必填, 同 opType 重复抛 OpErrorCode.OP_CONFIG_DUPLICATE */
    String createOpConfig(String opType,
                          String name,
                          Integer execTimeoutSeconds,
                          Integer waitTimeoutSeconds,
                          Integer maxRetry,
                          Boolean enabled,
                          String description);

    /** 更新 Op 配置; 写后发布 {@link com.nook.biz.operation.api.event.OpConfigChangedEvent} */
    void updateOpConfig(String id,
                        String name,
                        Integer execTimeoutSeconds,
                        Integer waitTimeoutSeconds,
                        Integer maxRetry,
                        Boolean enabled,
                        String description);

    /** 删除 Op 配置; 该 opType 失去配置, 入队会因 isEnabled=false 被拒 */
    void deleteOpConfig(String id);
}
