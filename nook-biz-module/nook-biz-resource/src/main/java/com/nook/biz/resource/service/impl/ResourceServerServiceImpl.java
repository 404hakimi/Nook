package com.nook.biz.resource.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
import com.nook.common.web.error.CommonErrorCode;
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
    private final ResourceServerValidator serverValidator;

    @Override
    public ResourceServer findById(String id) {
        requireId(id);
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
        serverValidator.validateForCreate(reqVO);
        ResourceServer e = new ResourceServer();
        e.setName(reqVO.getName());
        e.setHost(reqVO.getHost());
        e.setSshPort(reqVO.getSshPort());
        e.setSshUser(reqVO.getSshUser());
        e.setSshPassword(reqVO.getSshPassword());
        e.setSshTimeoutSeconds(reqVO.getSshTimeoutSeconds());
        e.setSshOpTimeoutSeconds(reqVO.getSshOpTimeoutSeconds());
        e.setSshUploadTimeoutSeconds(reqVO.getSshUploadTimeoutSeconds());
        e.setInstallTimeoutSeconds(reqVO.getInstallTimeoutSeconds());
        e.setTotalBandwidth(reqVO.getTotalBandwidth());
        e.setMonthlyTrafficGb(reqVO.getMonthlyTrafficGb());
        e.setIdcProvider(reqVO.getIdcProvider());
        e.setRegion(reqVO.getRegion());
        e.setStatus(reqVO.getStatus());
        e.setRemark(reqVO.getRemark());
        resourceServerMapper.insert(e);
        return e;
    }

    @Override
    public ResourceServer update(String id, ResourceServerSaveReqVO reqVO) {
        serverValidator.validateForUpdate(reqVO);
        ResourceServer exist = findById(id);

        if (StrUtil.isNotBlank(reqVO.getName()) && !StrUtil.equals(reqVO.getName(), exist.getName())) {
            serverValidator.validateNameUnique(id, reqVO.getName());
            exist.setName(reqVO.getName());
        }
        if (StrUtil.isNotBlank(reqVO.getHost()) && !StrUtil.equals(reqVO.getHost(), exist.getHost())) {
            serverValidator.validateHostUnique(id, reqVO.getHost());
            exist.setHost(reqVO.getHost());
        }
        if (ObjectUtil.isNotNull(reqVO.getSshPort())) exist.setSshPort(reqVO.getSshPort());
        if (StrUtil.isNotBlank(reqVO.getSshUser())) exist.setSshUser(reqVO.getSshUser());
        // sshPassword 留空 = 保留旧值, 非空才覆盖
        if (StrUtil.isNotBlank(reqVO.getSshPassword())) exist.setSshPassword(reqVO.getSshPassword());
        if (ObjectUtil.isNotNull(reqVO.getSshTimeoutSeconds())) exist.setSshTimeoutSeconds(reqVO.getSshTimeoutSeconds());
        if (ObjectUtil.isNotNull(reqVO.getSshOpTimeoutSeconds())) exist.setSshOpTimeoutSeconds(reqVO.getSshOpTimeoutSeconds());
        if (ObjectUtil.isNotNull(reqVO.getSshUploadTimeoutSeconds())) exist.setSshUploadTimeoutSeconds(reqVO.getSshUploadTimeoutSeconds());
        if (ObjectUtil.isNotNull(reqVO.getInstallTimeoutSeconds())) exist.setInstallTimeoutSeconds(reqVO.getInstallTimeoutSeconds());
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

    private static void requireId(String id) {
        if (StrUtil.isBlank(id)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "id 不能为空");
        }
    }
}
