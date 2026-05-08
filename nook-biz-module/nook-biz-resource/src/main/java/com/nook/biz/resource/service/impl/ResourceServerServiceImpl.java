package com.nook.biz.resource.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.resource.api.event.ServerCredentialChangedEvent;
import com.nook.biz.resource.constant.ResourceErrorCode;
import com.nook.biz.resource.controller.server.vo.ResourceServerPageReqVO;
import com.nook.biz.resource.controller.server.vo.ResourceServerSaveReqVO;
import com.nook.biz.resource.convert.ResourceServerConvert;
import com.nook.biz.resource.entity.ResourceServer;
import com.nook.biz.resource.mapper.ResourceServerMapper;
import com.nook.biz.resource.service.ResourceServerService;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceServerServiceImpl implements ResourceServerService {

    private final ResourceServerMapper resourceServerMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public ResourceServer findById(String id) {
        ResourceServer e = resourceServerMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NOT_FOUND, id);
        }
        return e;
    }

    @Override
    public PageResult<ResourceServer> page(ResourceServerPageReqVO reqVO) {
        IPage<ResourceServer> result = resourceServerMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public ResourceServer create(ResourceServerSaveReqVO reqVO) {
        validateOnCreate(reqVO);
        if (resourceServerMapper.existsByName(reqVO.getName())) {
            throw new BusinessException(ResourceErrorCode.SERVER_NAME_DUPLICATE, reqVO.getName());
        }
        if (resourceServerMapper.existsByHost(reqVO.getHost())) {
            throw new BusinessException(ResourceErrorCode.SERVER_HOST_DUPLICATE, reqVO.getHost());
        }
        ResourceServer e = new ResourceServer();
        e.setName(reqVO.getName());
        e.setHost(reqVO.getHost());
        e.setSshPort(reqVO.getSshPort() == null ? 22 : reqVO.getSshPort());
        e.setSshUser(StrUtil.blankToDefault(reqVO.getSshUser(), "root"));
        e.setSshPassword(StrUtil.blankToDefault(reqVO.getSshPassword(), null));
        e.setSshPrivateKey(StrUtil.blankToDefault(reqVO.getSshPrivateKey(), null));
        e.setSshTimeoutSeconds(reqVO.getSshTimeoutSeconds() == null ? 30 : reqVO.getSshTimeoutSeconds());
        e.setBackendTimeoutSeconds(reqVO.getBackendTimeoutSeconds() == null ? 20 : reqVO.getBackendTimeoutSeconds());
        e.setXrayGrpcHost(StrUtil.blankToDefault(reqVO.getXrayGrpcHost(), null));
        e.setXrayGrpcPort(reqVO.getXrayGrpcPort());
        e.setTotalBandwidth(reqVO.getTotalBandwidth() == null ? 1000 : reqVO.getTotalBandwidth());
        e.setMonthlyTrafficGb(reqVO.getMonthlyTrafficGb());
        e.setIdcProvider(reqVO.getIdcProvider());
        e.setRegion(reqVO.getRegion());
        e.setStatus(reqVO.getStatus() == null ? 1 : reqVO.getStatus());
        e.setRemark(reqVO.getRemark());
        resourceServerMapper.insert(e);
        return e;
    }

    @Override
    public ResourceServer update(String id, ResourceServerSaveReqVO reqVO) {
        ResourceServer exist = findById(id);
        if (StrUtil.isNotBlank(reqVO.getName())
                && !StrUtil.equals(reqVO.getName(), exist.getName())
                && resourceServerMapper.existsByNameExcludingId(reqVO.getName(), id)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NAME_DUPLICATE, reqVO.getName());
        }
        if (StrUtil.isNotBlank(reqVO.getHost())
                && !StrUtil.equals(reqVO.getHost(), exist.getHost())
                && resourceServerMapper.existsByHostExcludingId(reqVO.getHost(), id)) {
            throw new BusinessException(ResourceErrorCode.SERVER_HOST_DUPLICATE, reqVO.getHost());
        }
        // null 表示保留原值；非 null 才写入。密码字段同理(留空 == 保持旧值)。
        if (StrUtil.isNotBlank(reqVO.getName())) exist.setName(reqVO.getName());
        if (StrUtil.isNotBlank(reqVO.getHost())) exist.setHost(reqVO.getHost());
        if (ObjectUtil.isNotNull(reqVO.getSshPort())) exist.setSshPort(reqVO.getSshPort());
        if (StrUtil.isNotBlank(reqVO.getSshUser())) exist.setSshUser(reqVO.getSshUser());
        if (StrUtil.isNotBlank(reqVO.getSshPassword())) exist.setSshPassword(reqVO.getSshPassword());
        if (StrUtil.isNotBlank(reqVO.getSshPrivateKey())) exist.setSshPrivateKey(reqVO.getSshPrivateKey());
        if (ObjectUtil.isNotNull(reqVO.getSshTimeoutSeconds())) exist.setSshTimeoutSeconds(reqVO.getSshTimeoutSeconds());
        if (ObjectUtil.isNotNull(reqVO.getBackendTimeoutSeconds())) exist.setBackendTimeoutSeconds(reqVO.getBackendTimeoutSeconds());
        if (StrUtil.isNotBlank(reqVO.getXrayGrpcHost())) exist.setXrayGrpcHost(reqVO.getXrayGrpcHost());
        if (ObjectUtil.isNotNull(reqVO.getXrayGrpcPort())) exist.setXrayGrpcPort(reqVO.getXrayGrpcPort());
        if (ObjectUtil.isNotNull(reqVO.getTotalBandwidth())) exist.setTotalBandwidth(reqVO.getTotalBandwidth());
        if (ObjectUtil.isNotNull(reqVO.getMonthlyTrafficGb())) exist.setMonthlyTrafficGb(reqVO.getMonthlyTrafficGb());
        if (StrUtil.isNotBlank(reqVO.getIdcProvider())) exist.setIdcProvider(reqVO.getIdcProvider());
        if (StrUtil.isNotBlank(reqVO.getRegion())) exist.setRegion(reqVO.getRegion());
        if (ObjectUtil.isNotNull(reqVO.getStatus())) exist.setStatus(reqVO.getStatus());
        if (StrUtil.isNotBlank(reqVO.getRemark())) exist.setRemark(reqVO.getRemark());
        resourceServerMapper.updateById(exist);
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
        return exist;
    }

    @Override
    public void delete(String id) {
        ResourceServer exist = findById(id);
        resourceServerMapper.deleteById(exist.getId());
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    public ServerCredentialDTO loadCredential(String id) {
        return ResourceServerConvert.INSTANCE.toCredential(findById(id));
    }

    /** Create 校验：xray gRPC 地址必填 + SSH 鉴权(密码或私钥)必给一个. */
    private void validateOnCreate(ResourceServerSaveReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getXrayGrpcHost()) || ObjectUtil.isNull(reqVO.getXrayGrpcPort())) {
            throw new BusinessException(ResourceErrorCode.SERVER_GRPC_FIELDS_REQUIRED);
        }
        if (StrUtil.isBlank(reqVO.getSshPassword()) && StrUtil.isBlank(reqVO.getSshPrivateKey())) {
            throw new BusinessException(ResourceErrorCode.SERVER_SSH_AUTH_REQUIRED);
        }
    }
}
