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
import com.nook.biz.xray.controller.inbound.vo.XrayInboundUpdateReqVO;
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
            // 注意：这里抛的是 INBOUND_ENTITY_NOT_FOUND(DB 行)，与 CLIENT_NOT_FOUND(远端 client)语义不同
            throw new BusinessException(XrayErrorCode.INBOUND_ENTITY_NOT_FOUND, id);
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
        // 同 (memberUserId, ipId) 唯一；@TableLogic 让 selectByMemberAndIp 自动跳过软删行，
        // 所以"先 revoke 再 provision"的复用流程不会撞这个 duplicate 检查
        XrayInbound dup = xrayInboundMapper.selectByMemberAndIp(reqVO.getMemberUserId(), reqVO.getIpId());
        if (ObjectUtil.isNotNull(dup)) {
            throw new BusinessException(XrayErrorCode.CLIENT_DUPLICATE,
                    "memberUserId=" + reqVO.getMemberUserId() + " ipId=" + reqVO.getIpId());
        }

        ServerCredentialDTO cred = resourceServerApi.loadCredential(reqVO.getServerId());
        XrayBackend backend = xrayBackendFactory.get(cred);

        String clientUuid = UUID.randomUUID().toString();
        // email 格式 member_{memberId}_{ipId}: 同 server 全局唯一，便于人肉对账与日志检索
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

        // 先调远端：addClient 抛错则没有副作用(DB 也没写)；DB insert 抛错则留下"幽灵 client"——
        // 远端有 client 但本地无映射。下次 provision 同 (member, ip) 不会撞，但会再造一个；
        // 真正清理依赖反向 reconciler(列远端 - 减去 DB = 孤儿)，本期未实现。
        backend.addClient(spec);

        XrayInbound e = new XrayInbound();
        e.setServerId(reqVO.getServerId());
        e.setIpId(reqVO.getIpId());
        e.setMemberUserId(reqVO.getMemberUserId());
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
        // 先 del 旧；远端报 CLIENT_NOT_FOUND 视为已删成功(目标状态本就是没了)
        try {
            backend.delClient(new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail()));
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        // 再 add 新；如果 add 失败，远端此时既没有旧 client 也没有新 client，
        // 而 DB 里 client_uuid 还指向已被删的旧 UUID——必须把行标 status=3(待同步)
        // 让 reconciler 介入，否则后续 revoke/getTraffic 都会无脑去拉那个不存在的 UUID
        XrayClientSpec spec = XrayClientSpec.builder()
                .externalInboundRef(e.getExternalInboundRef())
                .email(e.getClientEmail())
                .uuid(newUuid)
                .protocol(e.getProtocol())
                .build();
        try {
            backend.addClient(spec);
        } catch (RuntimeException addErr) {
            log.error("rotate 在 del→add 中间失败 server={} email={}, 标 status=3 待 reconciler 修复",
                    e.getServerId(), e.getClientEmail(), addErr);
            xrayInboundMapper.updateStatus(e.getId(), 3, java.time.LocalDateTime.now());
            throw addErr;
        }

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

    @Override
    public XrayInbound update(String inboundEntityId, XrayInboundUpdateReqVO reqVO) {
        XrayInbound e = findById(inboundEntityId);
        // 只允许改本地元数据；不与 backend 同步——这些字段不影响远端 client 的实际行为
        if (StrUtil.isNotBlank(reqVO.getListenIp())) e.setListenIp(reqVO.getListenIp());
        if (ObjectUtil.isNotNull(reqVO.getListenPort())) e.setListenPort(reqVO.getListenPort());
        if (StrUtil.isNotBlank(reqVO.getTransport())) e.setTransport(reqVO.getTransport());
        if (ObjectUtil.isNotNull(reqVO.getStatus())) e.setStatus(reqVO.getStatus());
        xrayInboundMapper.updateById(e);
        return e;
    }

    private XrayBackend backendOf(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        return xrayBackendFactory.get(cred);
    }
}
