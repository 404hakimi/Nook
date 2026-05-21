package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerSaveReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.enums.ResourceErrorCode;
import com.nook.biz.node.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.event.ServerCredentialChangedEvent;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 资源服务器 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServerServiceImpl implements ResourceServerService {

    private final ResourceServerMapper resourceServerMapper;
    private final ResourceServerCapacityMapper resourceServerCapacityMapper;
    private final ResourceServerRuntimeMapper resourceServerRuntimeMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ResourceServerValidator serverValidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createServer(ResourceServerSaveReqVO createReqVO) {
        serverValidator.validateNameUnique(null, createReqVO.getName());
        serverValidator.validateHostUnique(null, createReqVO.getHost());
        // domain 可空, 只有非空时才查重 (LIVE 前置才必填)
        serverValidator.validateDomainUnique(null, createReqVO.getDomain());

        ResourceServerDO entity = BeanUtils.toBean(createReqVO, ResourceServerDO.class);
        // agent_token 入库时一次性签发, install / reinstall / upgrade 都复用 (永不刷新)
        entity.setAgentToken(generateAgentToken());
        resourceServerMapper.insert(entity);
        // 同步创建子表占位行 (capacity 中频 / runtime 高频); 没有 INSERT 就 SELECT JOIN 拿不到
        initCapacityAndRuntime(entity.getId());
        return entity.getId();
    }

    /** SHA256(UUID + UUID) → 64 char hex; 跟 DB CHAR(64) 长度对齐. */
    private static String generateAgentToken() {
        String raw = UUID.randomUUID() + UUID.randomUUID().toString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    /** 新建 server 时初始化 capacity + runtime 子表; 默认 NORMAL / 心跳 null. */
    private void initCapacityAndRuntime(String serverId) {
        LocalDateTime now = LocalDateTime.now();

        ResourceServerCapacityDO capacity = new ResourceServerCapacityDO();
        capacity.setServerId(serverId);
        capacity.setUsedTrafficBytes(0L);
        capacity.setQuotaResetPolicy("CALENDAR_MONTH");
        capacity.setThrottleState("NORMAL");
        capacity.setCreatedAt(now);
        capacity.setUpdatedAt(now);
        resourceServerCapacityMapper.insert(capacity);

        ResourceServerRuntimeDO runtime = new ResourceServerRuntimeDO();
        runtime.setServerId(serverId);
        runtime.setTempUnhealthy(0);
        runtime.setConsecutiveMiss(0);
        runtime.setUpdatedAt(now);
        resourceServerRuntimeMapper.insert(runtime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateServer(String id, ResourceServerSaveReqVO updateReqVO) {
        serverValidator.validateExists(id);
        serverValidator.validateNameUnique(id, updateReqVO.getName());
        serverValidator.validateHostUnique(id, updateReqVO.getHost());
        serverValidator.validateDomainUnique(id, updateReqVO.getDomain());

        // 更新服务器
        ResourceServerDO updateObj = BeanUtils.toBean(updateReqVO, ResourceServerDO.class);
        resourceServerMapper.update(updateObj, Wrappers.<ResourceServerDO>lambdaUpdate().eq(ResourceServerDO::getId, id));
        // 凭据变更事件; SshSessionManager 据此清掉该 server 的 SSH 会话缓存, 下次调用走新凭据
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteServer(String id) {
        // 校验服务器存在
        serverValidator.validateExists(id);
        // 删除服务器
        resourceServerMapper.deleteById(id);
        // 凭据变更事件; 让 SshSessionManager 立即关闭并释放该 server 的 SSH 连接
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    public ResourceServerDO getServer(String id) {
        return resourceServerMapper.selectById(id);
    }

    @Override
    public PageResult<ResourceServerDO> getServerPage(ResourceServerPageReqVO pageReqVO) {
        IPage<ResourceServerDO> result = resourceServerMapper.selectPageByQuery(
                Page.of(pageReqVO.getPageNo(), pageReqVO.getPageSize()), pageReqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public Map<String, ResourceServerDO> getServerMap(Collection<String> ids) {
        if (CollectionUtils.isAnyEmpty(ids)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids), ResourceServerDO::getId);
    }

    @Override
    public Map<String, String> getServerNameMap(Collection<String> ids) {
        if (CollectionUtils.isAnyEmpty(ids)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(ids),
                ResourceServerDO::getId,
                // name 缺失时 fallback host, 不让 UI 出现空白
                e -> e.getName() != null ? e.getName() : e.getHost());
    }

    // 允许的双向流转表; 命名按 from→to, 没列出的组合都拒
    private static final Set<String> ALLOWED_LIFECYCLE_TRANSITIONS = Set.of(
            "INSTALLING→READY", "READY→INSTALLING",  // 装机回退
            "READY→LIVE", "LIVE→READY",              // 上下线
            "LIVE→RETIRED", "READY→RETIRED",         // 退役
            "RETIRED→LIVE"                            // 退役复活回 LIVE; 不允许 RETIRED→READY 等
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transitionLifecycle(String id, String newState) {
        ResourceServerDO srv = serverValidator.validateExists(id);
        if (ResourceServerLifecycleEnum.fromState(newState) == null) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIFECYCLE_INVALID_TRANSITION,
                    srv.getLifecycleState(), newState);
        }
        if (StrUtil.equals(srv.getLifecycleState(), newState)) {
            return;  // 幂等: 目标态等于当前态直接 no-op
        }
        String key = srv.getLifecycleState() + "→" + newState;
        if (!ALLOWED_LIFECYCLE_TRANSITIONS.contains(key)) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIFECYCLE_INVALID_TRANSITION,
                    srv.getLifecycleState(), newState);
        }
        // LIVE 前置: domain 必填 (用户连接的子域名 / DNS 切换都靠它)
        if (ResourceServerLifecycleEnum.LIVE.matches(newState) && StrUtil.isBlank(srv.getDomain())) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIVE_DOMAIN_REQUIRED);
        }
        resourceServerMapper.updateLifecycleState(id, newState);
        log.info("[server] LIFECYCLE id={} {} → {}", id, srv.getLifecycleState(), newState);
    }
}
