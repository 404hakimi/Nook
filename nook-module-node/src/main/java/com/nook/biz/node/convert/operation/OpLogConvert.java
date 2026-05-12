package com.nook.biz.node.convert.operation;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.operation.vo.OpLogRespVO;
import com.nook.biz.operation.persistence.OpLog;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 操作日志 Convert
 *
 * @author nook
 */
@Mapper
public interface OpLogConvert {

    OpLogConvert INSTANCE = Mappers.getMapper(OpLogConvert.class);

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

    /**
     * 列表 + 名称回填.
     *
     * @param page          DO 分页
     * @param serverNames   Map of serverId → serverName
     * @param operatorNames Map of operatorId → operatorName
     * @param targetNames   Map of targetId → targetName
     * @return VO 分页
     */
    default PageResult<OpLogRespVO> convertPageWithInfo(PageResult<OpLog> page,
                                                       Map<String, String> serverNames,
                                                       Map<String, String> operatorNames,
                                                       Map<String, String> targetNames) {
        List<OpLogRespVO> rows = page.getRecords().stream().map(this::convertForList).toList();
        for (OpLogRespVO v : rows) {
            fillNames(v, serverNames, operatorNames, targetNames);
        }
        return PageResult.of(page.getTotal(), rows);
    }

    /** 详情 + 名称回填. */
    default OpLogRespVO convertForDetailWithInfo(OpLog entity,
                                                  Map<String, String> serverNames,
                                                  Map<String, String> operatorNames,
                                                  Map<String, String> targetNames) {
        OpLogRespVO vo = convertForDetail(entity);
        if (vo != null) {
            fillNames(vo, serverNames, operatorNames, targetNames);
        }
        return vo;
    }

    /** 抽 serverId 去重集合; 供 controller 批量查 server. */
    static Set<String> extractServerIds(Collection<OpLog> entities) {
        return CollectionUtils.convertSet(entities, OpLog::getServerId, e -> StrUtil.isNotBlank(e.getServerId()));
    }

    /** 抽 operator 去重集合 (含系统占位符如 SYSTEM / SCHEDULER). */
    static Set<String> extractOperatorIds(Collection<OpLog> entities) {
        return CollectionUtils.convertSet(entities, OpLog::getOperator, e -> StrUtil.isNotBlank(e.getOperator()));
    }

    /** 抽 targetId 去重集合 (server 级 op 没 targetId, 过滤掉). */
    static Set<String> extractTargetIds(Collection<OpLog> entities) {
        return CollectionUtils.convertSet(entities, OpLog::getTargetId, e -> StrUtil.isNotBlank(e.getTargetId()));
    }

    /** 算 started_at → ended_at 的毫秒差; 任一为 null 返 null. */
    static Long elapsedMs(OpLog e) {
        if (e == null || e.getStartedAt() == null || e.getEndedAt() == null) return null;
        return Duration.between(e.getStartedAt(), e.getEndedAt()).toMillis();
    }

    private static void fillNames(OpLogRespVO vo,
                                  Map<String, String> serverNames,
                                  Map<String, String> operatorNames,
                                  Map<String, String> targetNames) {
        if (StrUtil.isBlank(vo.getServerName()) && StrUtil.isNotBlank(vo.getServerId())) {
            // 缺失就 fallback 到原 id, 不让 UI 出现完全空白
            vo.setServerName(safeGet(serverNames, vo.getServerId(), vo.getServerId()));
        }
        if (StrUtil.isBlank(vo.getOperatorName()) && StrUtil.isNotBlank(vo.getOperator())) {
            vo.setOperatorName(safeGet(operatorNames, vo.getOperator(), vo.getOperator()));
        }
        if (StrUtil.isBlank(vo.getTargetName()) && StrUtil.isNotBlank(vo.getTargetId())) {
            vo.setTargetName(safeGet(targetNames, vo.getTargetId(), vo.getTargetId()));
        }
    }

    private static String safeGet(Map<String, String> map, String key, String fallback) {
        if (map == null) return fallback;
        String v = map.get(key);
        return v != null ? v : fallback;
    }
}
