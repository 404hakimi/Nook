package com.nook.biz.node.service.xray.client;

import jakarta.annotation.Resource;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.convert.xray.client.XrayClientConvert;
import com.nook.biz.node.service.xray.config.XrayConfigSyncService;
import com.nook.biz.node.framework.xray.grpc.XrayInboundClient;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.biz.node.framework.xray.grpc.UserTraffic;
import com.nook.biz.node.framework.xray.grpc.XrayStatsClient;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class XrayClientServiceImpl implements XrayClientService {

    /** 用户上行流量字节数; 参数: email. */
    public static final String USER_UPLINK_FORMAT = "user>>>%s>>>traffic>>>uplink";

    /** 用户下行流量字节数; 参数: email. */
    public static final String USER_DOWNLINK_FORMAT = "user>>>%s>>>traffic>>>downlink";

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayInboundClient xrayInboundClient;
    @Resource
    private XrayStatsClient xrayStatsClient;
    @Resource
    private ResourceServerApi resourceServerApi;
    @Resource
    private XrayConfigSyncService xrayConfigSyncService;

    @Override
    public XrayClientDO findById(String id) {
        XrayClientDO e = xrayClientMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            // CLIENT_ENTITY_NOT_FOUND = DB 行不存在; 与 CLIENT_NOT_FOUND (远端 client 不存在) 语义区分
            throw new BusinessException(XrayErrorCode.CLIENT_ENTITY_NOT_FOUND, id);
        }
        return e;
    }

    @Override
    public PageResult<XrayClientDO> page(ClientPageReqVO reqVO) {
        IPage<XrayClientDO> result = xrayClientMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public XrayClientDO provision(ClientProvisionReqVO reqVO) {
        // @TableLogic 让 selectByMemberAndIp 自动跳过软删行, 所以"先 revoke 再 provision"的复用流程不会撞 duplicate
        XrayClientDO dup = xrayClientMapper.selectByMemberAndIp(reqVO.getMemberUserId(), reqVO.getIpId());
        if (ObjectUtil.isNotNull(dup)) {
            throw new BusinessException(XrayErrorCode.CLIENT_DUPLICATE,
                    "memberUserId=" + reqVO.getMemberUserId() + " ipId=" + reqVO.getIpId());
        }

        String clientUuid = UUID.randomUUID().toString();
        // email 格式 member_{memberId}_{ipId}: 同 server 全局唯一, 便于人肉对账与日志检索
        String clientEmail = "member_" + reqVO.getMemberUserId() + "_" + reqVO.getIpId();

        InboundUserSpec spec = InboundUserSpec.builder()
                .externalInboundRef(reqVO.getExternalInboundRef())
                .email(clientEmail)
                .uuid(clientUuid)
                .protocol(reqVO.getProtocol())
                .flow(StrUtil.blankToDefault(reqVO.getFlow(), ""))
                .totalBytes(reqVO.getTotalBytes() == null ? 0L : reqVO.getTotalBytes())
                .expiryEpochMillis(reqVO.getExpiryEpochMillis() == null ? 0L : reqVO.getExpiryEpochMillis())
                .limitIp(reqVO.getLimitIp() == null ? 0 : reqVO.getLimitIp())
                .build();

        // 先调远端: addUser 失败则没有副作用 (DB 也没写); DB insert 失败留下"幽灵 client" 由 reconciler 清理 (本期未实现)
        xrayInboundClient.addUser(reqVO.getServerId(), spec.externalInboundRef(), spec);

        XrayClientDO e = new XrayClientDO();
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

        // 出站/路由刷盘并 reload, 让流量真的走对应 socks5 + 重启后仍生效;
        // 失败抛出由全局拦截器返回前端 (user 已在 xray 运行时存在但未绑定出站, 由后续巡检修复)
        xrayConfigSyncService.sync(reqVO.getServerId());
        return e;
    }

    @Override
    public void revoke(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        try {
            xrayInboundClient.removeUser(e.getServerId(), e.getExternalInboundRef(), e.getClientEmail());
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
        // user 已被 gRPC 删, reconcile 仅是清场, 失败仅 warn 不抛 — 留给后续巡检兜底, 不影响 revoke 主语义
        try {
            xrayConfigSyncService.sync(e.getServerId());
        } catch (RuntimeException reconErr) {
            log.warn("[revoke] reconcile 失败 server={} email={}, outbound/rule 残留",
                    e.getServerId(), e.getClientEmail(), reconErr);
        }
    }

    @Override
    public XrayClientDO rotate(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);

        String newUuid = UUID.randomUUID().toString();
        // 先 del 旧; 远端报 CLIENT_NOT_FOUND 视为已删成功 (目标状态本就是没了)
        try {
            xrayInboundClient.removeUser(e.getServerId(), e.getExternalInboundRef(), e.getClientEmail());
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        // 再 add 新; add 失败时远端没有任一 client, DB client_uuid 还指向已被删的旧 UUID —
        // 必须把行标 status=3(待同步) 让 reconciler 介入, 否则后续 revoke/getTraffic 会拉一个不存在的 UUID
        InboundUserSpec spec = InboundUserSpec.builder()
                .externalInboundRef(e.getExternalInboundRef())
                .email(e.getClientEmail())
                .uuid(newUuid)
                .protocol(e.getProtocol())
                .build();
        try {
            xrayInboundClient.addUser(e.getServerId(), spec.externalInboundRef(), spec);
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
    public ClientTrafficRespVO getTraffic(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        long up = xrayStatsClient.readStat(e.getServerId(),
                String.format(USER_UPLINK_FORMAT, e.getClientEmail()), false);
        long down = xrayStatsClient.readStat(e.getServerId(),
                String.format(USER_DOWNLINK_FORMAT, e.getClientEmail()), false);
        // 流量上限 / 到期 / enabled 在 gRPC 模式下由 nook subscription 控制, 远端不维护
        UserTraffic t = new UserTraffic(e.getClientEmail(), up, down, 0L, 0L, true);
        return XrayClientConvert.INSTANCE.toTrafficVO(e, t);
    }

    @Override
    public void resetTraffic(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        // reset=true 原子返回旧值并清零, 上下行各调一次; 返回值丢弃
        xrayStatsClient.readStat(e.getServerId(),
                String.format(USER_UPLINK_FORMAT, e.getClientEmail()), true);
        xrayStatsClient.readStat(e.getServerId(),
                String.format(USER_DOWNLINK_FORMAT, e.getClientEmail()), true);
    }

    @Override
    public XrayClientDO update(String inboundEntityId, ClientUpdateReqVO reqVO) {
        XrayClientDO e = findById(inboundEntityId);
        // 只允许改本地元数据; 不与远端同步——这些字段不影响远端 client 的实际行为
        if (StrUtil.isNotBlank(reqVO.getListenIp())) e.setListenIp(reqVO.getListenIp());
        if (ObjectUtil.isNotNull(reqVO.getListenPort())) e.setListenPort(reqVO.getListenPort());
        if (StrUtil.isNotBlank(reqVO.getTransport())) e.setTransport(reqVO.getTransport());
        if (ObjectUtil.isNotNull(reqVO.getStatus())) e.setStatus(reqVO.getStatus());
        xrayClientMapper.updateById(e);
        return e;
    }

    @Override
    public ClientCredentialRespVO loadCredential(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        ClientCredentialRespVO vo = new ClientCredentialRespVO();
        vo.setId(e.getId());
        vo.setClientUuid(e.getClientUuid());
        vo.setClientEmail(e.getClientEmail());
        vo.setProtocol(e.getProtocol());
        // 只为渲染分享链接, 取 ssh host 作为客户端连接 host (与 server 公网 IP 相同)
        vo.setServerHost(resourceServerApi.loadCredential(e.getServerId()).sshHost());
        vo.setListenPort(e.getListenPort());
        vo.setTransport(e.getTransport());
        return vo;
    }
}
