package com.nook.biz.xray.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.backend.XrayBackend;
import com.nook.biz.xray.backend.XrayBackendFactory;
import com.nook.biz.xray.backend.dto.XrayClientRef;
import com.nook.biz.xray.backend.dto.XrayClientSpec;
import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundPageReqVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundProvisionReqVO;
import com.nook.biz.xray.entity.XrayInbound;
import com.nook.biz.xray.mapper.XrayInboundMapper;
import com.nook.biz.xray.service.XrayInboundService;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class XrayInboundServiceImpl implements XrayInboundService {

    private final XrayInboundMapper xrayInboundMapper;
    private final XrayBackendFactory xrayBackendFactory;
    private final ResourceServerApi resourceServerApi;

    @Override
    public XrayInbound findById(String id) {
        XrayInbound e = xrayInboundMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(XrayErrorCode.CLIENT_NOT_FOUND, id);
        }
        return e;
    }

    @Override
    public PageResult<XrayInbound> page(XrayInboundPageReqVO reqVO) {
        IPage<XrayInbound> result = xrayInboundMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public List<XrayInboundInfo> listRemoteInbounds(String serverId) {
        return backendOf(serverId).listInbounds();
    }

    @Override
    public long verifyConnectivity(String serverId) {
        long t0 = System.currentTimeMillis();
        backendOf(serverId).verifyConnectivity();
        return System.currentTimeMillis() - t0;
    }

    @Override
    public XrayInbound provision(XrayInboundProvisionReqVO reqVO) {
        // 同 (memberUserId, ipId) 唯一；已存在直接抛错
        XrayInbound dup = xrayInboundMapper.selectByMemberAndIp(reqVO.getMemberUserId(), reqVO.getIpId());
        if (ObjectUtil.isNotNull(dup)) {
            throw new BusinessException(XrayErrorCode.CLIENT_DUPLICATE,
                    "memberUserId=" + reqVO.getMemberUserId() + " ipId=" + reqVO.getIpId());
        }

        ServerCredentialDTO cred = resourceServerApi.loadCredential(reqVO.getServerId());
        XrayBackend backend = xrayBackendFactory.get(cred);

        String clientUuid = UUID.randomUUID().toString();
        // 推荐 email 格式: member_{memberId}_{ipId}; 同时也保证 server 内全局唯一
        String clientEmail = "member_" + reqVO.getMemberUserId() + "_" + reqVO.getIpId();

        XrayClientSpec spec = XrayClientSpec.builder()
                .externalInboundRef(reqVO.getExternalInboundRef())
                .email(clientEmail)
                .uuid(clientUuid)
                .protocol(reqVO.getProtocol())
                .flow(StrUtil.blankToDefault(reqVO.getFlow(), ""))
                .totalBytes(reqVO.getTotalBytes() == null ? 0L : reqVO.getTotalBytes())
                .expiryEpochMillis(reqVO.getExpiryEpochMillis() == null ? 0L : reqVO.getExpiryEpochMillis())
                .limitIp(reqVO.getLimitIp() == null ? 0 : reqVO.getLimitIp())
                .build();

        // 先调远端，成功后再落 DB——失败留一个干净状态
        backend.addClient(spec);

        XrayInbound e = new XrayInbound();
        e.setServerId(reqVO.getServerId());
        e.setIpId(reqVO.getIpId());
        e.setMemberUserId(reqVO.getMemberUserId());
        e.setBackendType(cred.backendType());
        e.setExternalInboundRef(reqVO.getExternalInboundRef());
        e.setProtocol(reqVO.getProtocol());
        e.setTransport(reqVO.getTransport());
        e.setListenIp(reqVO.getListenIp());
        e.setListenPort(reqVO.getListenPort());
        e.setClientUuid(clientUuid);
        e.setClientEmail(clientEmail);
        e.setStatus(1);
        e.setLastSyncedAt(LocalDateTime.now());
        xrayInboundMapper.insert(e);
        return e;
    }

    @Override
    public void revoke(String inboundEntityId) {
        XrayInbound e = findById(inboundEntityId);
        ServerCredentialDTO cred = resourceServerApi.loadCredential(e.getServerId());
        XrayBackend backend = xrayBackendFactory.get(cred);
        try {
            backend.delClient(new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail()));
        } catch (BusinessException be) {
            // 远端已不存在也认为吊销成功——目标状态本来就是"没了"
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) {
                throw be;
            }
            log.warn("远端 client 已不存在，仅做 DB 软删: serverId={} email={}", e.getServerId(), e.getClientEmail());
        }
        xrayInboundMapper.deleteById(e.getId());
    }

    @Override
    public XrayInbound rotate(String inboundEntityId) {
        XrayInbound e = findById(inboundEntityId);
        ServerCredentialDTO cred = resourceServerApi.loadCredential(e.getServerId());
        XrayBackend backend = xrayBackendFactory.get(cred);

        String newUuid = UUID.randomUUID().toString();
        // 先 del 旧；远端报 CLIENT_NOT_FOUND 视为已删成功
        try {
            backend.delClient(new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail()));
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        // 再 add 新；如果失败这条 inbound 当前没有可用 client，需要 reconciler 修复或重试 rotate
        XrayClientSpec spec = XrayClientSpec.builder()
                .externalInboundRef(e.getExternalInboundRef())
                .email(e.getClientEmail())
                .uuid(newUuid)
                .protocol(e.getProtocol())
                .build();
        backend.addClient(spec);

        xrayInboundMapper.updateClientUuid(e.getId(), newUuid);
        e.setClientUuid(newUuid);
        return e;
    }

    @Override
    public XrayClientTraffic getTraffic(String inboundEntityId) {
        XrayInbound e = findById(inboundEntityId);
        ServerCredentialDTO cred = resourceServerApi.loadCredential(e.getServerId());
        XrayBackend backend = xrayBackendFactory.get(cred);
        return backend.getClientTraffic(
                new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail()));
    }

    @Override
    public void resetTraffic(String inboundEntityId) {
        XrayInbound e = findById(inboundEntityId);
        ServerCredentialDTO cred = resourceServerApi.loadCredential(e.getServerId());
        XrayBackend backend = xrayBackendFactory.get(cred);
        backend.resetClientTraffic(
                new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail()));
    }

    private XrayBackend backendOf(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        return xrayBackendFactory.get(cred);
    }
}
