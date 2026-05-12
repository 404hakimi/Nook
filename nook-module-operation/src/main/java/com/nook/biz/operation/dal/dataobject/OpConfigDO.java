package com.nook.biz.operation.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * op_config 表实体
 *
 * @author nook
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("op_config")
public class OpConfigDO extends BaseEntity {

    /** OpType 枚举名 */
    private String opType;

    /** 中文显示名 */
    private String name;

    /** handler 执行超时秒数 (watchdog 切线程) */
    private Integer execTimeoutSeconds;

    /** caller 阻塞等待秒数 (submitAndWait) */
    private Integer waitTimeoutSeconds;

    /** 失败自动重试次数 (保留, 当前未启用) */
    private Integer maxRetry;

    /** 是否启用; 关闭后该 op 入队抛错 */
    private Boolean enabled;

    /** 备注 */
    private String description;
}
