package com.nook.biz.operation.internal.resolver;

import com.nook.biz.operation.api.event.OpConfigChangedEvent;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import com.nook.biz.operation.dal.mysql.mapper.OpConfigMapper;
import com.nook.biz.operation.enums.OpErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Op 配置解析器: 严格以 op_config 表为唯一来源.
 *
 * <p>读语义: DB 命中 → 取行字段; DB 无行 / 字段为空 → 抛 OP_CONFIG_NOT_FOUND, 不再有 yml 或代码兜底.
 * admin 必须为每个用到的 OpType 在 op_config 配齐 enabled / exec_timeout / wait_timeout 三列;
 * 唯一例外是 {@link #isEnabled} 在 DB 无行时返 false (语义即"未配置=禁用"), 让 enqueue 拒绝.
 *
 * <p>cache: ConcurrentMap 懒填充, {@link OpConfigChangedEvent} 触发 invalidate.
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultOpConfigResolver implements OpConfigResolver {

    private final OpConfigMapper opConfigMapper;

    private final ConcurrentMap<String, OpConfigDO> cache = new ConcurrentHashMap<>();

    /** 哨兵: 表示 "已查过 DB, 确认无此 opType 行" */
    private static final OpConfigDO NULL_HOLDER = OpConfigDO.builder().opType("__NULL__").build();

    @Override
    public Duration getExecTimeout(String opType) {
        OpConfigDO row = lookup(opType);
        if (row == null || row.getExecTimeoutSeconds() == null) {
            throw new BusinessException(OpErrorCode.OP_CONFIG_NOT_FOUND, opType);
        }
        return Duration.ofSeconds(row.getExecTimeoutSeconds());
    }

    @Override
    public Duration getWaitTimeout(String opType) {
        OpConfigDO row = lookup(opType);
        if (row == null || row.getWaitTimeoutSeconds() == null) {
            throw new BusinessException(OpErrorCode.OP_CONFIG_NOT_FOUND, opType);
        }
        return Duration.ofSeconds(row.getWaitTimeoutSeconds());
    }

    @Override
    public int getMaxRetry(String opType) {
        OpConfigDO row = lookup(opType);
        // max_retry 字段允许空 (向后兼容 admin 未填), 视为 0 次重试; 与 timeout 类必填字段不同
        return row != null && row.getMaxRetry() != null ? row.getMaxRetry() : 0;
    }

    @Override
    public boolean isEnabled(String opType) {
        OpConfigDO row = lookup(opType);
        // 未配置 = 禁用; enqueue 拒绝, 不抛配置缺失错让运维误以为系统挂了
        if (row == null) return false;
        return row.getEnabled() != null && row.getEnabled();
    }

    @EventListener
    public void onConfigChanged(OpConfigChangedEvent event) {
        if (event.opType() == null) {
            cache.clear();
        } else {
            cache.remove(event.opType());
        }
        log.info("[op-config] cache invalidated (trigger: {})", event.opType());
    }

    private OpConfigDO lookup(String opType) {
        // computeIfAbsent: mappingFunction 抛异常时不缓存, 下轮重新查 DB; 抖动恢复后自然好
        OpConfigDO cached = cache.computeIfAbsent(opType, k -> {
            OpConfigDO row = opConfigMapper.selectByOpType(k);
            return row != null ? row : NULL_HOLDER;
        });
        return cached == NULL_HOLDER ? null : cached;
    }
}
