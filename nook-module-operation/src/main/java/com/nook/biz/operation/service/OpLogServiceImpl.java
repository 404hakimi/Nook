package com.nook.biz.operation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.operation.controller.vo.OpLogPageReqVO;
import com.nook.biz.operation.controller.vo.OpLogRespVO;
import com.nook.biz.operation.convert.OpLogConvert;
import com.nook.biz.operation.mapper.OpLogQueryMapper;
import com.nook.biz.operation.api.OpErrorCode;
import com.nook.biz.operation.api.OperationOrchestrator;
import com.nook.biz.operation.persistence.OpLog;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author nook
 */
@Service
public class OpLogServiceImpl implements OpLogService {

    @Resource
    private OpLogQueryMapper opLogQueryMapper;
    @Resource
    private OperationOrchestrator operationOrchestrator;

    @Override
    public PageResult<OpLogRespVO> page(OpLogPageReqVO reqVO) {
        // OpType enum 入参 → 持久层用 String, 用 .name() 转
        String opTypeStr = reqVO.getOpType() == null ? null : reqVO.getOpType().name();
        IPage<OpLog> page = opLogQueryMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()),
                reqVO.getStatus(), reqVO.getServerId(), opTypeStr);
        PageResult<OpLog> raw = PageResult.of(page.getTotal(), page.getRecords());
        return OpLogConvert.INSTANCE.convertPageForList(raw);
    }

    @Override
    public OpLogRespVO findById(String id) {
        OpLog entity = opLogQueryMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(OpErrorCode.HANDLER_NOT_FOUND, "opId=" + id);
        }
        return OpLogConvert.INSTANCE.convertForDetail(entity);
    }

    @Override
    public boolean cancelQueued(String id) {
        return operationOrchestrator.cancelQueued(id);
    }
}
