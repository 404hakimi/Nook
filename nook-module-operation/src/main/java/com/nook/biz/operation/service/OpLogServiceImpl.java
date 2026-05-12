package com.nook.biz.operation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.operation.api.OpErrorCode;
import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.OperationOrchestrator;
import com.nook.biz.operation.mapper.OpLogQueryMapper;
import com.nook.biz.operation.persistence.OpLog;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * op_log 查询服务实现.
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpLogServiceImpl implements OpLogService {

    private final OpLogQueryMapper opLogQueryMapper;
    private final OperationOrchestrator operationOrchestrator;

    @Override
    public PageResult<OpLog> page(int pageNo, int pageSize, OpStatus status, String serverId, OpType opType) {
        // OpType enum 入参 → 持久层用 String, 用 .name() 转
        String opTypeStr = opType == null ? null : opType.name();
        IPage<OpLog> page = opLogQueryMapper.selectPageByQuery(
                Page.of(pageNo, pageSize), status, serverId, opTypeStr);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public OpLog findById(String id) {
        OpLog entity = opLogQueryMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(OpErrorCode.HANDLER_NOT_FOUND, "opId=" + id);
        }
        return entity;
    }

    @Override
    public boolean cancelQueued(String id) {
        return operationOrchestrator.cancelQueued(id);
    }
}
