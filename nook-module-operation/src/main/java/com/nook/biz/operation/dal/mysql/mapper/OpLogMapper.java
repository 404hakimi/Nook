package com.nook.biz.operation.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nook.biz.operation.dal.dataobject.OpLogDO;
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
public interface OpLogMapper extends BaseMapper<OpLogDO> {

    /**
     * QUEUED → RUNNING, 同时记录 started_at; 抢成功返 1, 已被取消等返 0.
     */
    @Update("UPDATE op_log SET status='RUNNING', started_at=#{startedAt} " +
            "WHERE id=#{id} AND status='QUEUED'")
    int casQueuedToRunning(@Param("id") String id, @Param("startedAt") LocalDateTime startedAt);

    /**
     * QUEUED → CANCELLED, 同时清 active_key 让同三元组可重入; 仅 QUEUED 生效.
     * current_step 写"已取消"避免 UI 停留在历史步骤名上.
     */
    @Update("UPDATE op_log SET status='CANCELLED', active_key=NULL, ended_at=#{endedAt}, " +
            "current_step='已取消' " +
            "WHERE id=#{id} AND status='QUEUED'")
    int casQueuedToCancelled(@Param("id") String id, @Param("endedAt") LocalDateTime endedAt);

    /**
     * RUNNING → DONE, 清 active_key; 同步把 current_step 改成"已完成" + progress_pct=100,
     * 防止 UI 列表停留在 handler 中途的 "正在 xxx" 步骤名.
     */
    @Update("UPDATE op_log SET status='DONE', active_key=NULL, ended_at=#{endedAt}, " +
            "progress_pct=100, current_step='已完成' " +
            "WHERE id=#{id} AND status='RUNNING'")
    int casRunningToDone(@Param("id") String id, @Param("endedAt") LocalDateTime endedAt);

    /**
     * RUNNING → FAILED, 清 active_key; current_step 改成"已失败"贴合最终态.
     */
    @Update("UPDATE op_log SET status='FAILED', active_key=NULL, ended_at=#{endedAt}, " +
            "current_step='已失败', error_code=#{errorCode}, error_msg=#{errorMsg} " +
            "WHERE id=#{id} AND status='RUNNING'")
    int casRunningToFailed(@Param("id") String id,
                           @Param("endedAt") LocalDateTime endedAt,
                           @Param("errorCode") String errorCode,
                           @Param("errorMsg") String errorMsg);

    /**
     * RUNNING → TIMED_OUT, 清 active_key; watchdog 用. current_step 改成"已超时".
     */
    @Update("UPDATE op_log SET status='TIMED_OUT', active_key=NULL, ended_at=#{endedAt}, " +
            "current_step='已超时', error_code='TIMED_OUT', error_msg=#{errorMsg} " +
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
            "current_step='已失败', error_code='WORKER_LOST', " +
            "error_msg='应用重启时该 op 仍在 RUNNING, 视为僵尸' " +
            "WHERE status='RUNNING'")
    int cleanupZombieRunning();

    /**
     * 按 active_key 找当前活跃的 op_id (DuplicateKey 异常后告知调用方"已有谁在跑").
     */
    @org.apache.ibatis.annotations.Select(
            "SELECT id FROM op_log WHERE active_key=#{activeKey} LIMIT 1")
    String findActiveIdByKey(@Param("activeKey") String activeKey);
}
