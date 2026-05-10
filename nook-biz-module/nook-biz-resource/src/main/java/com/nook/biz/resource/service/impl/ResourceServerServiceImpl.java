package com.nook.biz.resource.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.api.event.ServerCredentialChangedEvent;
import com.nook.biz.resource.controller.server.vo.ResourceServerPageReqVO;
import com.nook.biz.resource.controller.server.vo.ResourceServerSaveReqVO;
import com.nook.biz.resource.convert.ResourceServerConvert;
import com.nook.biz.resource.entity.ResourceServer;
import com.nook.biz.resource.mapper.ResourceServerMapper;
import com.nook.biz.resource.service.ResourceServerService;
import com.nook.biz.resource.validator.ResourceServerValidator;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceServerServiceImpl implements ResourceServerService {

    private final ResourceServerMapper resourceServerMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ResourceServerValidator serverValidator;

    @Override
    public ResourceServer findById(String id) {
        return serverValidator.validateExists(id);
    }

    @Override
    public PageResult<ResourceServer> page(ResourceServerPageReqVO reqVO) {
        IPage<ResourceServer> result = resourceServerMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public ResourceServer create(ResourceServerSaveReqVO reqVO) {
        // 校验别名唯一
        serverValidator.validateNameUnique(null, reqVO.getName());
        // 校验主机唯一
        serverValidator.validateHostUnique(null, reqVO.getHost());

        // 插入服务器
        ResourceServer entity = BeanUtils.toBean(reqVO, ResourceServer.class);
        resourceServerMapper.insert(entity);
        return entity;
    }

    @Override
    public void update(String id, ResourceServerSaveReqVO reqVO) {
        // 校验服务器存在
        serverValidator.validateExists(id);
        // 校验别名唯一
        serverValidator.validateNameUnique(id, reqVO.getName());
        // 校验主机唯一
        serverValidator.validateHostUnique(id, reqVO.getHost());

        // 更新服务器
        ResourceServer entity = BeanUtils.toBean(reqVO, ResourceServer.class);
        resourceServerMapper.update(entity, Wrappers.<ResourceServer>lambdaUpdate().eq(ResourceServer::getId, id));
        // 发布凭据变更事件: SshSessionManager 据此清掉该 server 的 SSH 会话缓存, 下次调用走新凭据
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    public void delete(String id) {
        // 校验服务器存在
        serverValidator.validateExists(id);
        // 删除服务器
        resourceServerMapper.deleteById(id);
        // 发布凭据变更事件: 让 SshSessionManager 立即关闭并释放该 server 的 SSH 连接
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    public ServerCredentialDTO loadCredential(String id) {
        return ResourceServerConvert.INSTANCE.toCredential(findById(id));
    }
}
