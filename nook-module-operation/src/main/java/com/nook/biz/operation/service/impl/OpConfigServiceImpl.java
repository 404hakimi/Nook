package com.nook.biz.operation.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.operation.api.event.OpConfigChangedEvent;
import com.nook.biz.operation.enums.OpErrorCode;
import com.nook.biz.operation.config.OpTimeoutProperties;
import com.nook.biz.operation.dal.mysql.mapper.OpConfigMapper;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import com.nook.biz.operation.service.OpConfigService;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
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
    private final OpTimeoutProperties opTimeoutProperties;
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
    public void updateOpConfig(String id,
                               Integer execTimeoutSeconds,
                               Integer waitTimeoutSeconds,
                               Integer maxRetry,
                               Boolean enabled,
                               String description) {
        OpConfigDO row = getOpConfig(id);
        // op_type / name 由 Bootstrapper 维护, admin 不应改
        OpConfigDO update = OpConfigDO.builder()
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetOpConfig(String id) {
        OpConfigDO row = getOpConfig(id);
        int exec = (int) opTimeoutProperties.execOf(row.getOpType()).getSeconds();
        int wait = (int) opTimeoutProperties.waitOf(row.getOpType()).getSeconds();
        updateOpConfig(id, exec, wait, 0, Boolean.TRUE, null);
        log.info("[op-config] reset to yml opType={} exec={}s wait={}s", row.getOpType(), exec, wait);
    }

    @Override
    public boolean insertIfAbsent(OpConfigDO opConfig) {
        try {
            opConfigMapper.insert(opConfig);
            log.info("[op-config] insert opType={} exec={}s wait={}s",
                    opConfig.getOpType(), opConfig.getExecTimeoutSeconds(), opConfig.getWaitTimeoutSeconds());
            return true;
        } catch (DuplicateKeyException dke) {
            return false;
        }
    }
}
