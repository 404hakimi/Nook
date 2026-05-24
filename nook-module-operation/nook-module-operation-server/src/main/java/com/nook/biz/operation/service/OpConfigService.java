package com.nook.biz.operation.service;

import com.nook.biz.operation.dal.dataobject.OpConfigDO;

import java.util.List;

/**
 * Op 配置 Service 接口
 *
 * @author nook
 */
public interface OpConfigService {

    /**
     * 获得 op 配置列表
     *
     * @return op 配置列表
     */
    List<OpConfigDO> getOpConfigList();

    /**
     * 获得 op 配置
     *
     * @param id op 配置编号
     * @return op 配置
     */
    OpConfigDO getOpConfig(String id);

    /**
     * 按 opType 获得 op 配置
     *
     * @param opType OpType.name() 字符串
     * @return op 配置
     */
    OpConfigDO getOpConfigByType(String opType);

    /**
     * 创建 op 配置
     *
     * @param opType             OpType.name()
     * @param name               显示名
     * @param execTimeoutSeconds 执行超时秒数
     * @param waitTimeoutSeconds 等待超时秒数
     * @param maxRetry           失败重试次数
     * @param enabled            是否启用
     * @param description        备注
     * @return op 配置编号
     */
    String createOpConfig(String opType,
                          String name,
                          Integer execTimeoutSeconds,
                          Integer waitTimeoutSeconds,
                          Integer maxRetry,
                          Boolean enabled,
                          String description);

    /**
     * 更新 op 配置
     *
     * @param id                 op 配置编号
     * @param name               显示名
     * @param execTimeoutSeconds 执行超时秒数
     * @param waitTimeoutSeconds 等待超时秒数
     * @param maxRetry           失败重试次数
     * @param enabled            是否启用
     * @param description        备注
     */
    void updateOpConfig(String id,
                        String name,
                        Integer execTimeoutSeconds,
                        Integer waitTimeoutSeconds,
                        Integer maxRetry,
                        Boolean enabled,
                        String description);

    /**
     * 删除 op 配置
     *
     * @param id op 配置编号
     */
    void deleteOpConfig(String id);
}
