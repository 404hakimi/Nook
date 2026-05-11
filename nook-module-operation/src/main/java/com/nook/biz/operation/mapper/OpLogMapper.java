package com.nook.biz.operation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nook.biz.operation.persistence.OpLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 框架内核用的 op_log 状态机数据访问; 仅 orchestrator / watchdog / startup-cleaner 调用,
 * 业务侧 UI 查询另外用 biz 模块的 mapper, 不要在这里加 selectPageByQuery / selectList 之类的 list 查询.
 *
 * <p>所有状态变迁走 status 旧值校验的 CAS UPDATE, 保证并发正确.
 *
 * @author nook
 */
@Mapper
public interface OpLogMapper extends BaseMapper<OpLog> {

    /**
     * QUEUED → RUNNING, 同时记录 started_at; 抢成功返 1, 已被取消等返 0.
     */
    @Update("UPDATE op_log SET status='RUNNING', started_at=#{startedAt} " +
            "WHERE id=#{id} AND status='QUEUED'")
    int casQueuedToRunning(@Param("id") String id, @Param("startedAt") LocalDateTime startedAt);

    /**
     * QUEUED → CANCELLED, 同时清 active_key 让同三元组可重入; 仅 QUEUED 生效.
     */
    @Update("UPDATE op_log SET status='CANCELLED', active_key=NULL, ended_at=#{endedAt} " +
            "WHERE id=#{id} AND status='QUEUED'")
    int casQueuedToCancelled(@Param("id") String id, @Param("endedAt") LocalDateTime endedAt);

    /**
     * RUNNING → DONE, 清 active_key.
     */
    @Update("UPDATE op_log SET status='DONE', active_key=NULL, ended_at=#{endedAt}, progress_pct=100 " +
            "WHERE id=#{id} AND status='RUNNING'")
    int casRunningToDone(@Param("id") String id, @Param("endedAt") LocalDateTime endedAt);

    /**
     * RUNNING → FAILED, 清 active_key.
     */
    @Update("UPDATE op_log SET status='FAILED', active_key=NULL, ended_at=#{endedAt}, " +
            "error_code=#{errorCode}, error_msg=#{errorMsg} " +
            "WHERE id=#{id} AND status='RUNNING'")
    int casRunningToFailed(@Param("id") String id,
                           @Param("endedAt") LocalDateTime endedAt,
                           @Param("errorCode") String errorCode,
                           @Param("errorMsg") String errorMsg);

    /**
     * RUNNING → TIMED_OUT, 清 active_key; watchdog 用.
     */
    @Update("UPDATE op_log SET status='TIMED_OUT', active_key=NULL, ended_at=#{endedAt}, " +
            "error_code='TIMED_OUT', error_msg=#{errorMsg} " +
            "WHERE id=#{id} AND status='RUNNING'")
    int casRunningToTimedOut(@Param("id") String id,
                             @Param("endedAt") LocalDateTime endedAt,
                             @Param("errorMsg") String errorMsg);

    /**
     * 进度更新; 不影响 status.
     */
    @Update("UPDATE op_log SET current_step=#{step}, progress_pct=#{pct}, last_message=#{message} " +
            "WHERE id=#{id} AND status='RUNNING'")
    int updateProgress(@Param("id") String id,
                       @Param("step") String step,
                       @Param("pct") Integer pct,
                       @Param("message") String message);

    /**
     * 启动清理: 上次崩溃残留的 RUNNING 全标 FAILED(WORKER_LOST).
     */
    @Update("UPDATE op_log SET status='FAILED', active_key=NULL, ended_at=NOW(), " +
            "error_code='WORKER_LOST', error_msg='应用重启时该 op 仍在 RUNNING, 视为僵尸' " +
            "WHERE status='RUNNING'")
    int cleanupZombieRunning();

    /**
     * 按 active_key 找当前活跃的 op_id (DuplicateKey 异常后告知调用方"已有谁在跑").
     */
    @org.apache.ibatis.annotations.Select(
            "SELECT id FROM op_log WHERE active_key=#{activeKey} LIMIT 1")
    String findActiveIdByKey(@Param("activeKey") String activeKey);
}
