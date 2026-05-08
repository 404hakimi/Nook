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
import com.nook.biz.xray.controller.client.vo.XrayClientCredentialRespVO;
import com.nook.biz.xray.controller.client.vo.XrayClientPageReqVO;
import com.nook.biz.xray.controller.client.vo.XrayClientProvisionReqVO;
import com.nook.biz.xray.controller.client.vo.XrayClientUpdateReqVO;
import com.nook.biz.xray.entity.XrayClient;
import com.nook.biz.xray.mapper.XrayClientMapper;
import com.nook.biz.xray.service.XrayConfigReconciler;
import com.nook.biz.xray.service.XrayClientService;
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
public class XrayClientServiceImpl implements XrayClientService {

    private final XrayClientMapper xrayClientMapper;
    private final XrayBackendFactory xrayBackendFactory;
    private final ResourceServerApi resourceServerApi;
    private final XrayConfigReconciler xrayConfigReconciler;

    @Override
    public XrayClient findById(String id) {
        XrayClient e = xrayClientMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            // CLIENT_ENTITY_NOT_FOUND = DB 行不存在; 与 CLIENT_NOT_FOUND (远端 client 不存在) 语义区分
            throw new BusinessException(XrayErrorCode.CLIENT_ENTITY_NOT_FOUND, id);
        }
        return e;
    }

    @Override
    public PageResult<XrayClient> page(XrayClientPageReqVO reqVO) {
        IPage<XrayClient> result = xrayClientMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public List<XrayInboundInfo> listRemoteInbounds(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        return xrayBackendFactory.invoke(cred, XrayBackend::listInbounds);
    }

    @Override
    public long verifyConnectivity(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        long t0 = System.currentTimeMillis();
        xrayBackendFactory.invokeVoid(cred, XrayBackend::verifyConnectivity);
        return System.currentTimeMillis() - t0;
    }

    @Override
    public XrayClient provision(XrayClientProvisionReqVO reqVO) {
        // 同 (memberUserId, ipId) 唯一；@TableLogic 让 selectByMemberAndIp 自动跳过软删行，
        // 所以"先 revoke 再 provision"的复用流程不会撞这个 duplicate 检查
        XrayClient dup = xrayClientMapper.selectByMemberAndIp(reqVO.getMemberUserId(), reqVO.getIpId());
        if (ObjectUtil.isNotNull(dup)) {
            throw new BusinessException(XrayErrorCode.CLIENT_DUPLICATE,
                    "memberUserId=" + reqVO.getMemberUserId() + " ipId=" + reqVO.getIpId());
        }

        ServerCredentialDTO cred = resourceServerApi.loadCredential(reqVO.getServerId());

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

        // 先调远端: addClient 失败则没有副作用 (DB 也没写); DB insert 失败则留下"幽灵 client" —
        // 远端有 client 但本地无映射, 由后续反向 reconciler 清理 (本期未实现)。
        // invokeVoid: 收到 BACKEND_UNREACHABLE 时自动 markDead 重建 backend 重试一次。
        xrayBackendFactory.invokeVoid(cred, b -> b.addClient(spec));

        XrayClient e = new XrayClient();
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
        xrayClientMapper.insert(e);

        // 运行时 user 已通过 gRPC 添加; 这里把出站/路由刷盘并 reload, 让流量真的走对应 socks5 + 重启后仍生效。
        // 失败抛出由全局拦截器返回前端 (user 已在 xray 运行时存在但未绑定出站, 由后续巡检修复)。
        xrayConfigReconciler.reconcile(cred);
        return e;
    }

    @Override
    public void revoke(String inboundEntityId) {
        XrayClient e = findById(inboundEntityId);
        ServerCredentialDTO cred = resourceServerApi.loadCredential(e.getServerId());
        try {
            XrayClientRef ref = new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail());
            xrayBackendFactory.invokeVoid(cred, b -> b.delClient(ref));
        } catch (BusinessException be) {
            // 远端已不存在也认为吊销成功 — 目标状态本就是"没了"
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) {
                throw be;
            }
            log.warn("[revoke] 远端 client 已不存在, 仅做 DB 软删 server={} email={}",
                    e.getServerId(), e.getClientEmail());
        }
        xrayClientMapper.deleteById(e.getId());
        // 重写 xray.json 清理该 user 对应的 outbound + 路由 rule;
        // user 已被 gRPC 删, reconcile 仅是清场, 失败仅 warn 不抛 — 留给后续巡检兜底, 不影响 revoke 主语义。
        try {
            xrayConfigReconciler.reconcile(cred);
        } catch (RuntimeException reconErr) {
            log.warn("[revoke] reconcile 失败 server={} email={}, outbound/rule 残留",
                    e.getServerId(), e.getClientEmail(), reconErr);
        }
    }

    @Override
    public XrayClient rotate(String inboundEntityId) {
        XrayClient e = findById(inboundEntityId);
        ServerCredentialDTO cred = resourceServerApi.loadCredential(e.getServerId());

        String newUuid = UUID.randomUUID().toString();
        XrayClientRef oldRef = new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail());
        // 先 del 旧; 远端报 CLIENT_NOT_FOUND 视为已删成功 (目标状态本就是没了)
        try {
            xrayBackendFactory.invokeVoid(cred, b -> b.delClient(oldRef));
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        // 再 add 新; add 失败时远端没有任一 client, 但 DB client_uuid 还指向已被删的旧 UUID —
        // 必须把行标 status=3(待同步) 让 reconciler 介入, 否则后续 revoke/getTraffic 会拉一个不存在的 UUID。
        XrayClientSpec spec = XrayClientSpec.builder()
                .externalInboundRef(e.getExternalInboundRef())
                .email(e.getClientEmail())
                .uuid(newUuid)
                .protocol(e.getProtocol())
                .build();
        try {
            xrayBackendFactory.invokeVoid(cred, b -> b.addClient(spec));
        } catch (RuntimeException addErr) {
            log.error("[rotate] del 后 add 失败 server={} email={}, 标 status=3 待 reconciler 修复",
                    e.getServerId(), e.getClientEmail(), addErr);
            xrayClientMapper.updateStatus(e.getId(), 3, LocalDateTime.now());
            throw addErr;
        }

        xrayClientMapper.updateClientUuid(e.getId(), newUuid);
        e.setClientUuid(newUuid);
        return e;
    }

    @Override
    public XrayClientTraffic getTraffic(String inboundEntityId) {
        XrayClient e = findById(inboundEntityId);
        ServerCredentialDTO cred = resourceServerApi.loadCredential(e.getServerId());
        XrayClientRef ref = new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail());
        return xrayBackendFactory.invoke(cred, b -> b.getClientTraffic(ref));
    }

    @Override
    public void resetTraffic(String inboundEntityId) {
        XrayClient e = findById(inboundEntityId);
        ServerCredentialDTO cred = resourceServerApi.loadCredential(e.getServerId());
        XrayClientRef ref = new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail());
        xrayBackendFactory.invokeVoid(cred, b -> b.resetClientTraffic(ref));
    }

    @Override
    public XrayClient update(String inboundEntityId, XrayClientUpdateReqVO reqVO) {
        XrayClient e = findById(inboundEntityId);
        // 只允许改本地元数据；不与 backend 同步——这些字段不影响远端 client 的实际行为
        if (StrUtil.isNotBlank(reqVO.getListenIp())) e.setListenIp(reqVO.getListenIp());
        if (ObjectUtil.isNotNull(reqVO.getListenPort())) e.setListenPort(reqVO.getListenPort());
        if (StrUtil.isNotBlank(reqVO.getTransport())) e.setTransport(reqVO.getTransport());
        if (ObjectUtil.isNotNull(reqVO.getStatus())) e.setStatus(reqVO.getStatus());
        xrayClientMapper.updateById(e);
        return e;
    }

    @Override
    public XrayClientCredentialRespVO loadCredential(String inboundEntityId) {
        XrayClient e = findById(inboundEntityId);
        ServerCredentialDTO cred = resourceServerApi.loadCredential(e.getServerId());
        XrayClientCredentialRespVO vo = new XrayClientCredentialRespVO();
        vo.setId(e.getId());
        vo.setClientUuid(e.getClientUuid());
        vo.setClientEmail(e.getClientEmail());
        vo.setProtocol(e.getProtocol());
        vo.setServerHost(cred.sshHost());
        vo.setListenPort(e.getListenPort());
        vo.setTransport(e.getTransport());
        return vo;
    }
}
