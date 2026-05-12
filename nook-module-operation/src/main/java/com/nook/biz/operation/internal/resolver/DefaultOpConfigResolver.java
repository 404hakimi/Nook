package com.nook.biz.operation.internal.resolver;

import com.nook.biz.operation.api.event.OpConfigChangedEvent;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.config.OpTimeoutProperties;
import com.nook.biz.operation.dal.mysql.mapper.OpConfigMapper;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 默认 Op 配置解析器: ConcurrentMap 懒缓存, 由 {@link OpConfigChangedEvent} 失效
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultOpConfigResolver implements OpConfigResolver {

    private final OpConfigMapper opConfigMapper;
    private final OpTimeoutProperties opTimeoutProperties;

    private final ConcurrentMap<String, OpConfigDO> cache = new ConcurrentHashMap<>();

    /** 哨兵: 表示 "已查过 DB, 确认无此 opType 行" */
    private static final OpConfigDO NULL_HOLDER = OpConfigDO.builder().opType("__NULL__").build();

    @Override
    public Duration getExecTimeout(String opType) {
        OpConfigDO row = lookup(opType);
        if (row != null && row.getExecTimeoutSeconds() != null) {
            return Duration.ofSeconds(row.getExecTimeoutSeconds());
        }
        return opTimeoutProperties.execOf(opType);
    }

    @Override
    public Duration getWaitTimeout(String opType) {
        OpConfigDO row = lookup(opType);
        if (row != null && row.getWaitTimeoutSeconds() != null) {
            return Duration.ofSeconds(row.getWaitTimeoutSeconds());
        }
        return opTimeoutProperties.waitOf(opType);
    }

    @Override
    public int getMaxRetry(String opType) {
        OpConfigDO row = lookup(opType);
        return row != null && row.getMaxRetry() != null ? row.getMaxRetry() : 0;
    }

    @Override
    public boolean isEnabled(String opType) {
        OpConfigDO row = lookup(opType);
        // 无 DB 配置视为启用 (向后兼容新加 opType 还没 bootstrap 的情况)
        return row == null || row.getEnabled() == null || row.getEnabled();
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
        OpConfigDO cached = cache.computeIfAbsent(opType, k -> {
            try {
                OpConfigDO row = opConfigMapper.selectByOpType(k);
                return row != null ? row : NULL_HOLDER;
            } catch (Exception e) {
                // DB 不通 / 表缺失时降级到 yml fallback, 不污染业务路径
                log.warn("[op-config] DB 查询失败 opType={}, 降级 yml: {}", k, e.getMessage());
                return NULL_HOLDER;
            }
        });
        return cached == NULL_HOLDER ? null : cached;
    }
}
