package com.nook.biz.operation.internal.resolver;

import com.nook.biz.operation.api.event.OpConfigChangedEvent;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import com.nook.biz.operation.dal.mysql.mapper.OpConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Op 配置解析器: 以 op_config 表为唯一来源, ConcurrentMap 懒缓存
 *
 * <p>读语义: DB 命中 → 取行字段; DB 无行 → 视为未启用 (isEnabled=false), timeout 类返回安全兜底常量.
 * admin 必须为每个用到的 OpType 在 op_config 配一行, 否则 enqueue 会因 isEnabled=false 被拒.
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultOpConfigResolver implements OpConfigResolver {

    /** DB 缺行时的极端安全兜底, 业务正常情况不应触达 (enqueue 早已被 isEnabled=false 拦下) */
    private static final Duration SAFETY_EXEC_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration SAFETY_WAIT_TIMEOUT = Duration.ofSeconds(150);

    private final OpConfigMapper opConfigMapper;

    private final ConcurrentMap<String, OpConfigDO> cache = new ConcurrentHashMap<>();

    /** 哨兵: 表示 "已查过 DB, 确认无此 opType 行" */
    private static final OpConfigDO NULL_HOLDER = OpConfigDO.builder().opType("__NULL__").build();

    @Override
    public Duration getExecTimeout(String opType) {
        OpConfigDO row = lookup(opType);
        if (row != null && row.getExecTimeoutSeconds() != null) {
            return Duration.ofSeconds(row.getExecTimeoutSeconds());
        }
        log.warn("[op-config] exec timeout 缺 op_config 行, 走安全兜底 opType={}", opType);
        return SAFETY_EXEC_TIMEOUT;
    }

    @Override
    public Duration getWaitTimeout(String opType) {
        OpConfigDO row = lookup(opType);
        if (row != null && row.getWaitTimeoutSeconds() != null) {
            return Duration.ofSeconds(row.getWaitTimeoutSeconds());
        }
        log.warn("[op-config] wait timeout 缺 op_config 行, 走安全兜底 opType={}", opType);
        return SAFETY_WAIT_TIMEOUT;
    }

    @Override
    public int getMaxRetry(String opType) {
        OpConfigDO row = lookup(opType);
        return row != null && row.getMaxRetry() != null ? row.getMaxRetry() : 0;
    }

    @Override
    public boolean isEnabled(String opType) {
        OpConfigDO row = lookup(opType);
        // 强一致: 缺 op_config 行 = 未配置 = 禁用, 强制 admin 完整配置
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
        OpConfigDO cached = cache.computeIfAbsent(opType, k -> {
            try {
                OpConfigDO row = opConfigMapper.selectByOpType(k);
                return row != null ? row : NULL_HOLDER;
            } catch (Exception e) {
                // DB 不通时降级到 NULL_HOLDER, isEnabled 自然返 false, enqueue 会拒
                log.warn("[op-config] DB 查询失败 opType={}: {}", k, e.getMessage());
                return NULL_HOLDER;
            }
        });
        return cached == NULL_HOLDER ? null : cached;
    }
}
