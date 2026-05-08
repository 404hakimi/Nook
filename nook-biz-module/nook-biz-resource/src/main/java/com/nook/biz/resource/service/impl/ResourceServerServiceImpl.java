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
        validateBackendFields(reqVO, true);
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
        e.setBackendType(reqVO.getBackendType());
        e.setPanelBaseUrl(StrUtil.blankToDefault(reqVO.getPanelBaseUrl(), null));
        e.setPanelUsername(StrUtil.blankToDefault(reqVO.getPanelUsername(), null));
        e.setPanelPassword(StrUtil.blankToDefault(reqVO.getPanelPassword(), null));
        e.setPanelIgnoreTls(reqVO.getPanelIgnoreTls() == null ? 0 : reqVO.getPanelIgnoreTls());
        e.setBackendTimeoutSeconds(reqVO.getBackendTimeoutSeconds() == null ? 20 : reqVO.getBackendTimeoutSeconds());
        e.setXrayGrpcHost(StrUtil.blankToDefault(reqVO.getXrayGrpcHost(), null));
        e.setXrayGrpcPort(reqVO.getXrayGrpcPort());
        e.setTotalBandwidth(reqVO.getTotalBandwidth() == null ? 1000 : reqVO.getTotalBandwidth());
        e.setMonthlyTrafficGb(reqVO.getMonthlyTrafficGb()); // null = 不限/未配置
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
        validateBackendFields(reqVO, false);
        // name / host 改了要查重
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
        // null 表示保留原值；非 null 才写入。密码字段同理(留空 == 保持旧值)，要清密码请用专门端点(后续加)。
        if (StrUtil.isNotBlank(reqVO.getName())) exist.setName(reqVO.getName());
        if (StrUtil.isNotBlank(reqVO.getHost())) exist.setHost(reqVO.getHost());
        if (ObjectUtil.isNotNull(reqVO.getSshPort())) exist.setSshPort(reqVO.getSshPort());
        if (StrUtil.isNotBlank(reqVO.getSshUser())) exist.setSshUser(reqVO.getSshUser());
        if (StrUtil.isNotBlank(reqVO.getSshPassword())) exist.setSshPassword(reqVO.getSshPassword());
        if (StrUtil.isNotBlank(reqVO.getSshPrivateKey())) exist.setSshPrivateKey(reqVO.getSshPrivateKey());
        if (ObjectUtil.isNotNull(reqVO.getSshTimeoutSeconds())) exist.setSshTimeoutSeconds(reqVO.getSshTimeoutSeconds());
        if (StrUtil.isNotBlank(reqVO.getBackendType())) exist.setBackendType(reqVO.getBackendType());
        if (StrUtil.isNotBlank(reqVO.getPanelBaseUrl())) exist.setPanelBaseUrl(reqVO.getPanelBaseUrl());
        if (StrUtil.isNotBlank(reqVO.getPanelUsername())) exist.setPanelUsername(reqVO.getPanelUsername());
        if (StrUtil.isNotBlank(reqVO.getPanelPassword())) exist.setPanelPassword(reqVO.getPanelPassword());
        if (ObjectUtil.isNotNull(reqVO.getPanelIgnoreTls())) exist.setPanelIgnoreTls(reqVO.getPanelIgnoreTls());
        if (ObjectUtil.isNotNull(reqVO.getBackendTimeoutSeconds())) exist.setBackendTimeoutSeconds(reqVO.getBackendTimeoutSeconds());
        if (StrUtil.isNotBlank(reqVO.getXrayGrpcHost())) exist.setXrayGrpcHost(reqVO.getXrayGrpcHost());
        if (ObjectUtil.isNotNull(reqVO.getXrayGrpcPort())) exist.setXrayGrpcPort(reqVO.getXrayGrpcPort());
        if (ObjectUtil.isNotNull(reqVO.getTotalBandwidth())) exist.setTotalBandwidth(reqVO.getTotalBandwidth());
        // monthlyTrafficGb: null=不改；想清空回"不限"暂只能在 DB 直接改(后续扩接口)
        if (ObjectUtil.isNotNull(reqVO.getMonthlyTrafficGb())) exist.setMonthlyTrafficGb(reqVO.getMonthlyTrafficGb());
        if (StrUtil.isNotBlank(reqVO.getIdcProvider())) exist.setIdcProvider(reqVO.getIdcProvider());
        if (StrUtil.isNotBlank(reqVO.getRegion())) exist.setRegion(reqVO.getRegion());
        if (ObjectUtil.isNotNull(reqVO.getStatus())) exist.setStatus(reqVO.getStatus());
        if (StrUtil.isNotBlank(reqVO.getRemark())) exist.setRemark(reqVO.getRemark());
        resourceServerMapper.updateById(exist);
        // 发事件让 xray 模块的 backend cache 失效——不区分是否真的改了凭据，统一处理避免漏发
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
        return exist;
    }

    @Override
    public void delete(String id) {
        ResourceServer exist = findById(id);
        resourceServerMapper.deleteById(exist.getId());
        // 释放该 server 在 xray 那边的 backend / HttpClient / Channel 资源
        applicationEventPublisher.publishEvent(new ServerCredentialChangedEvent(id));
    }

    @Override
    public ServerCredentialDTO loadCredential(String id) {
        return ResourceServerConvert.INSTANCE.toCredential(findById(id));
    }

    /**
     * 校验 backend 类型对应的字段齐不齐。
     * isCreate=true 时严格要求；false 时若字段没传(为 null)说明用户没改，跳过严格校验。
     */
    private void validateBackendFields(ResourceServerSaveReqVO reqVO, boolean isCreate) {
        String type = reqVO.getBackendType();
        if (StrUtil.isBlank(type)) {
            if (isCreate) throw new BusinessException(ResourceErrorCode.SERVER_BACKEND_TYPE_INVALID);
            return;
        }
        switch (type) {
            case "threexui" -> {
                if (isCreate
                        && (StrUtil.isBlank(reqVO.getPanelBaseUrl())
                            || StrUtil.isBlank(reqVO.getPanelUsername())
                            || StrUtil.isBlank(reqVO.getPanelPassword()))) {
                    throw new BusinessException(ResourceErrorCode.SERVER_PANEL_FIELDS_REQUIRED);
                }
            }
            case "xray-grpc" -> {
                if (isCreate
                        && (StrUtil.isBlank(reqVO.getXrayGrpcHost())
                            || ObjectUtil.isNull(reqVO.getXrayGrpcPort()))) {
                    throw new BusinessException(ResourceErrorCode.SERVER_GRPC_FIELDS_REQUIRED);
                }
            }
            default -> throw new BusinessException(ResourceErrorCode.SERVER_BACKEND_TYPE_INVALID);
        }
        // SSH 鉴权(密码或私钥)在 create 时必须给一个；update 时若新值留空仍允许(保留旧的)
        if (isCreate
                && StrUtil.isBlank(reqVO.getSshPassword())
                && StrUtil.isBlank(reqVO.getSshPrivateKey())) {
            throw new BusinessException(ResourceErrorCode.SERVER_SSH_AUTH_REQUIRED);
        }
    }
}
