package com.nook.biz.operation.internal.startup;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.event.OpConfigChangedEvent;
import com.nook.biz.operation.config.OpTimeoutProperties;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import com.nook.biz.operation.service.OpConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 应用启动时把 OpType 枚举缺失的行补到 op_config 表
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
public class OpConfigBootstrapper {

    private final OpConfigService opConfigService;
    private final OpTimeoutProperties opTimeoutProperties;
    private final ApplicationEventPublisher applicationEventPublisher;

    @PostConstruct
    public void bootstrap() {
        // DB 不通 / 表缺失时也不能挂应用启动
        try {
            doBootstrap();
        } catch (Exception e) {
            log.error("[op-config] bootstrap 失败, 跳过; resolver 会走 yml fallback", e);
        }
    }

    private void doBootstrap() {
        int inserted = 0;
        for (OpType type : OpType.values()) {
            OpConfigDO row = OpConfigDO.builder()
                    .opType(type.name())
                    // 初值用 enum name(), admin 后续可在 op_config 页编辑
                    .name(type.name())
                    .execTimeoutSeconds((int) opTimeoutProperties.execOf(type.name()).getSeconds())
                    .waitTimeoutSeconds((int) opTimeoutProperties.waitOf(type.name()).getSeconds())
                    .maxRetry(0)
                    .enabled(Boolean.TRUE)
                    .build();
            if (opConfigService.insertIfAbsent(row)) {
                inserted++;
            }
        }
        if (inserted > 0) {
            log.info("[op-config] 启动期补齐 {} 行", inserted);
            // 清掉 bootstrap 前已被预热的 NULL 哨兵
            applicationEventPublisher.publishEvent(new OpConfigChangedEvent(null));
        }
    }
}
