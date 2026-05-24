package com.nook.biz.operation.service;

import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.dal.dataobject.OpLogDO;
import com.nook.common.web.response.PageResult;

/**
 * 操作日志 Service 接口
 *
 * @author nook
 */
public interface OpLogService {

    /**
     * 获得操作日志分页
     *
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @param status   状态过滤
     * @param serverId 服务器过滤
     * @param opType   操作类型过滤
     * @return 操作日志分页
     */
    PageResult<OpLogDO> page(int pageNo, int pageSize, OpStatus status, String serverId, OpType opType);

    /**
     * 获得操作日志
     *
     * @param id 操作日志编号
     * @return 操作日志
     */
    OpLogDO findById(String id);

    /**
     * 取消排队中的操作
     *
     * @param id 操作日志编号
     * @return 是否取消成功
     */
    boolean cancelQueued(String id);
}
