package com.nook.biz.node.service.resource.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerSaveReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.event.ServerCredentialChangedEvent;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ResourceServerValidator serverValidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createServer(ResourceServerSaveReqVO createReqVO) {
        // 校验别名唯一
        serverValidator.validateNameUnique(null, createReqVO.getName());
        // 校验主机唯一
        serverValidator.validateHostUnique(null, createReqVO.getHost());

        // 插入服务器
        ResourceServerDO entity = BeanUtils.toBean(createReqVO, ResourceServerDO.class);
        resourceServerMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateServer(String id, ResourceServerSaveReqVO updateReqVO) {
        // 校验服务器存在
        serverValidator.validateExists(id);
        // 校验别名唯一
        serverValidator.validateNameUnique(id, updateReqVO.getName());
        // 校验主机唯一
        serverValidator.validateHostUnique(id, updateReqVO.getHost());

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
        return serverValidator.validateExists(id);
    }

    @Override
    public boolean hasServer(String id) {
        return resourceServerMapper.selectById(id) != null;
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
}
