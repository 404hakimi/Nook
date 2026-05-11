package com.nook.biz.operation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.operation.api.OpErrorCode;
import com.nook.biz.operation.api.OpLogEnricher;
import com.nook.biz.operation.api.OperationOrchestrator;
import com.nook.biz.operation.controller.vo.OpLogPageReqVO;
import com.nook.biz.operation.controller.vo.OpLogRespVO;
import com.nook.biz.operation.convert.OpLogConvert;
import com.nook.biz.operation.mapper.OpLogQueryMapper;
import com.nook.biz.operation.persistence.OpLog;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpLogServiceImpl implements OpLogService {

    private final OpLogQueryMapper opLogQueryMapper;
    private final OperationOrchestrator operationOrchestrator;
    /**
     * 名称回填扩展点; biz 模块按需声明 @Component 实现该接口, 这里按发现顺序串联调用.
     * 用 ObjectProvider 避免没有任何实现时 Spring 抱怨 — 没装就当空 list.
     */
    private final ObjectProvider<OpLogEnricher> enrichersProvider;

    @Override
    public PageResult<OpLogRespVO> page(OpLogPageReqVO reqVO) {
        // OpType enum 入参 → 持久层用 String, 用 .name() 转
        String opTypeStr = reqVO.getOpType() == null ? null : reqVO.getOpType().name();
        IPage<OpLog> page = opLogQueryMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()),
                reqVO.getStatus(), reqVO.getServerId(), opTypeStr);
        PageResult<OpLog> raw = PageResult.of(page.getTotal(), page.getRecords());
        PageResult<OpLogRespVO> result = OpLogConvert.INSTANCE.convertPageForList(raw);
        applyEnrichers(result.getRecords());
        return result;
    }

    @Override
    public OpLogRespVO findById(String id) {
        OpLog entity = opLogQueryMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(OpErrorCode.HANDLER_NOT_FOUND, "opId=" + id);
        }
        OpLogRespVO vo = OpLogConvert.INSTANCE.convertForDetail(entity);
        applyEnrichers(Collections.singletonList(vo));
        return vo;
    }

    @Override
    public boolean cancelQueued(String id) {
        return operationOrchestrator.cancelQueued(id);
    }

    /**
     * 串联调用所有 enricher; 单个失败不影响其余字段, log warn 后继续 — UI 缺名字总比整页 500 好.
     */
    private void applyEnrichers(List<OpLogRespVO> vos) {
        if (vos == null || vos.isEmpty()) return;
        enrichersProvider.orderedStream().forEach(e -> {
            try {
                e.enrich(vos);
            } catch (Exception ex) {
                log.warn("[op-log] enricher {} 执行失败, 跳过: {}", e.getClass().getSimpleName(), ex.getMessage());
            }
        });
    }
}
