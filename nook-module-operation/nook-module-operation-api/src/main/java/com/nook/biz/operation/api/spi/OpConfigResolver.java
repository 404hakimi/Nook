package com.nook.biz.operation.api.spi;

import java.time.Duration;

/**
 * Op 配置解析器: 业务侧唯一入口, 按 DB → yml → 代码兜底 三级 fallback
 *
 * @author nook
 */
public interface OpConfigResolver {

    /** handler 执行超时 (watchdog 切线程用) */
    Duration getExecTimeout(String opType);

    /** caller 阻塞等待超时 (submitAndWait 用) */
    Duration getWaitTimeout(String opType);

    /** 失败重试次数 */
    int getMaxRetry(String opType);

    /** 是否启用; admin 关停后入队应拒绝 */
    boolean isEnabled(String opType);
}
