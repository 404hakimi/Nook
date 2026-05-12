package com.nook.biz.operation.service;

import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.dal.dataobject.OpLogDO;
import com.nook.common.web.response.PageResult;

/**
 * op_log 查询 Service.
 *
 * @author nook
 */
public interface OpLogService {

    /**
     * 分页查询; 按 enqueued_at DESC 排.
     *
     * @param pageNo   页码 (从 1 起)
     * @param pageSize 每页条数
     * @param status   状态过滤; null = 不过滤
     * @param serverId server 过滤; blank = 不过滤
     * @param opType   操作类型过滤; null = 不过滤
     * @return DO 分页结果
     */
    PageResult<OpLogDO> page(int pageNo, int pageSize, OpStatus status, String serverId, OpType opType);

    /**
     * 按 id 加载; 不存在抛 BusinessException.
     *
     * @param id op_log.id
     * @return OpLogDO
     */
    OpLogDO findById(String id);

    /**
     * 取消队列中的 op; 仅 QUEUED 生效, 其余状态返 false.
     *
     * @param id op_log.id
     * @return 是否真的取消了
     */
    boolean cancelQueued(String id);
}
