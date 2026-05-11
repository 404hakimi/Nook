package com.nook.biz.operation.service;

import com.nook.biz.operation.controller.vo.OpLogPageReqVO;
import com.nook.biz.operation.controller.vo.OpLogRespVO;
import com.nook.common.web.response.PageResult;

/**
 * op_log 查询服务; 写路径 (入队 / 取消) 走 OperationOrchestrator, 不在这里.
 *
 * @author nook
 */
public interface OpLogService {

    /**
     * 分页查询; 按 enqueued_at DESC 排.
     *
     * @param reqVO 过滤参数
     * @return 分页结果
     */
    PageResult<OpLogRespVO> page(OpLogPageReqVO reqVO);

    /**
     * 单条详情; 含 paramsJson / errorMsg 等全量字段.
     *
     * @param id op_log.id
     * @return OpLogRespVO; 不存在抛 BusinessException
     */
    OpLogRespVO findById(String id);

    /**
     * 取消队列中的 op; 仅 QUEUED 生效, 其余状态返 false.
     *
     * @param id op_log.id
     * @return 是否真的取消了
     */
    boolean cancelQueued(String id);
}
