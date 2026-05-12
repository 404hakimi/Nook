package com.nook.biz.operation.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.operation.api.event.OpConfigChangedEvent;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import com.nook.biz.operation.dal.mysql.mapper.OpConfigMapper;
import com.nook.biz.operation.enums.OpErrorCode;
import com.nook.biz.operation.service.OpConfigService;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Op 配置 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpConfigServiceImpl implements OpConfigService {

    private final OpConfigMapper opConfigMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public List<OpConfigDO> getOpConfigList() {
        return opConfigMapper.selectAllOrdered();
    }

    @Override
    public OpConfigDO getOpConfig(String id) {
        OpConfigDO row = opConfigMapper.selectById(id);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(OpErrorCode.OP_CONFIG_NOT_FOUND, id);
        }
        return row;
    }

    @Override
    public OpConfigDO getOpConfigByType(String opType) {
        OpConfigDO row = opConfigMapper.selectByOpType(opType);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(OpErrorCode.OP_CONFIG_NOT_FOUND, opType);
        }
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOpConfig(String opType,
                                 String name,
                                 Integer execTimeoutSeconds,
                                 Integer waitTimeoutSeconds,
                                 Integer maxRetry,
                                 Boolean enabled,
                                 String description) {
        // 同 opType 重复拦截 (DB 也有 uk_op_type UNIQUE 双保险)
        if (opConfigMapper.selectByOpType(opType) != null) {
            throw new BusinessException(OpErrorCode.OP_CONFIG_DUPLICATE, opType);
        }
        OpConfigDO row = OpConfigDO.builder()
                .opType(opType)
                .name(name)
                .execTimeoutSeconds(execTimeoutSeconds)
                .waitTimeoutSeconds(waitTimeoutSeconds)
                .maxRetry(maxRetry == null ? 0 : maxRetry)
                .enabled(enabled == null ? Boolean.TRUE : enabled)
                .description(description)
                .build();
        opConfigMapper.insert(row);
        applicationEventPublisher.publishEvent(new OpConfigChangedEvent(opType));
        log.info("[op-config] create opType={} exec={}s wait={}s", opType, execTimeoutSeconds, waitTimeoutSeconds);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOpConfig(String id) {
        OpConfigDO row = getOpConfig(id);
        opConfigMapper.deleteById(id);
        applicationEventPublisher.publishEvent(new OpConfigChangedEvent(row.getOpType()));
        log.info("[op-config] delete opType={}, 该 opType 后续 enqueue 将被禁用", row.getOpType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOpConfig(String id,
                               String name,
                               Integer execTimeoutSeconds,
                               Integer waitTimeoutSeconds,
                               Integer maxRetry,
                               Boolean enabled,
                               String description) {
        OpConfigDO row = getOpConfig(id);
        // op_type 不允许改 (由 OpType 枚举绑死); name 允许改
        OpConfigDO update = OpConfigDO.builder()
                .name(name)
                .execTimeoutSeconds(execTimeoutSeconds)
                .waitTimeoutSeconds(waitTimeoutSeconds)
                .maxRetry(maxRetry)
                .enabled(enabled)
                .description(description)
                .build();
        opConfigMapper.update(update,
                Wrappers.<OpConfigDO>lambdaUpdate().eq(OpConfigDO::getId, id));
        applicationEventPublisher.publishEvent(new OpConfigChangedEvent(row.getOpType()));
    }
}
