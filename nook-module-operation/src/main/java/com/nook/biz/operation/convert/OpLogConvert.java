package com.nook.biz.operation.convert;

import com.nook.biz.operation.controller.vo.OpLogRespVO;
import com.nook.biz.operation.persistence.OpLog;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.util.List;

/**
 * OpLog 实体 ↔ VO; 列表场景去掉 paramsJson 避免占字段.
 *
 * @author nook
 */
@Mapper
public interface OpLogConvert {

    OpLogConvert INSTANCE = Mappers.getMapper(OpLogConvert.class);

    @Named("base")
    OpLogRespVO convert(OpLog entity);

    /** 列表用: 去掉 paramsJson + errorMsg (大字段), 加 elapsedMs. */
    default OpLogRespVO convertForList(OpLog entity) {
        OpLogRespVO vo = convert(entity);
        if (vo == null) return null;
        vo.setParamsJson(null);
        vo.setErrorMsg(null);
        vo.setElapsedMs(elapsedMs(entity));
        return vo;
    }

    /** 详情用: 全量字段 + elapsedMs. */
    default OpLogRespVO convertForDetail(OpLog entity) {
        OpLogRespVO vo = convert(entity);
        if (vo == null) return null;
        vo.setElapsedMs(elapsedMs(entity));
        return vo;
    }

    default PageResult<OpLogRespVO> convertPageForList(PageResult<OpLog> page) {
        List<OpLogRespVO> rows = page.getRecords().stream().map(this::convertForList).toList();
        return PageResult.of(page.getTotal(), rows);
    }

    /** 算 started_at → ended_at 的毫秒差; 任一为 null 返 null. */
    static Long elapsedMs(OpLog e) {
        if (e == null || e.getStartedAt() == null || e.getEndedAt() == null) return null;
        return Duration.between(e.getStartedAt(), e.getEndedAt()).toMillis();
    }
}
