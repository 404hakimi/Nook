package com.nook.biz.operation.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nook.biz.operation.dal.dataobject.OpLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * op_log 状态机访问层; 仅 orchestrator / watchdog / startup-cleaner 调用.
 *
 * <p>状态变迁全部走 status 旧值校验的 CAS UPDATE, 终态清 active_key 让同三元组可重入.
 * SQL 在 {@code resources/mapper/OpLogMapper.xml}.
 *
 * @author nook
 */
@Mapper
public interface OpLogMapper extends BaseMapper<OpLogDO> {

    /** QUEUED → RUNNING. */
    int casQueuedToRunning(@Param("id") String id, @Param("startedAt") LocalDateTime startedAt);

    /** QUEUED → CANCELLED. */
    int casQueuedToCancelled(@Param("id") String id, @Param("endedAt") LocalDateTime endedAt);

    /** RUNNING → DONE; 同步把 progress_pct=100 + current_step="已完成", 防 UI 停留在中途步骤名. */
    int casRunningToDone(@Param("id") String id, @Param("endedAt") LocalDateTime endedAt);

    /** RUNNING → FAILED. */
    int casRunningToFailed(@Param("id") String id,
                           @Param("endedAt") LocalDateTime endedAt,
                           @Param("errorCode") String errorCode,
                           @Param("errorMsg") String errorMsg);

    /** RUNNING → TIMED_OUT; watchdog 用. */
    int casRunningToTimedOut(@Param("id") String id,
                             @Param("endedAt") LocalDateTime endedAt,
                             @Param("errorMsg") String errorMsg);

    /** 仅更新进度, 不动 status. */
    int updateProgress(@Param("id") String id,
                       @Param("step") String step,
                       @Param("pct") Integer pct,
                       @Param("message") String message);

    /** 启动清理: 上次崩溃残留的 RUNNING 全标 FAILED(WORKER_LOST). */
    int cleanupZombieRunning();

    /** 按 active_key 找当前活跃 op_id; DuplicateKey 异常时告知"已有谁在跑". */
    String findActiveIdByKey(@Param("activeKey") String activeKey);
}
