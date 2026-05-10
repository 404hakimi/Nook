package com.nook.biz.node.service.xray.client;

import jakarta.annotation.Resource;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.convert.xray.client.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.handler.UserTraffic;
import com.nook.biz.node.framework.xray.handler.XrayInboundCliClient;
import com.nook.biz.node.framework.xray.handler.XrayOutboundCliClient;
import com.nook.biz.node.framework.xray.handler.XrayStatsCliClient;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.service.xray.slot.XraySlotPoolService;
import com.nook.biz.resource.api.ResourceIpPoolApi;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.IpPoolEntryDTO;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class XrayClientServiceImpl implements XrayClientService {

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayInboundCliClient inboundCli;
    @Resource
    private XrayOutboundCliClient outboundCli;
    @Resource
    private XrayStatsCliClient statsCli;
    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private XraySlotPoolService slotPoolService;
    @Resource
    private ResourceServerApi resourceServerApi;
    @Resource
    private ResourceIpPoolApi resourceIpPoolApi;

    @Override
    public XrayClientDO findById(String id) {
        XrayClientDO e = xrayClientMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
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

    /**
     * 开通客户 (1:1 模型 + slot 预分配 + CLI 增量).
     *
     * <p>步骤:
     * <ol>
     *   <li>DB 检 dup (memberId, ipId)</li>
     *   <li>占空闲 slot (事务内, SELECT FOR UPDATE)</li>
     *   <li>SSH+CLI 加 inbound (1:1 模型每客户独享一个 inbound)</li>
     *   <li>SSH+CLI 删占位 freedom outbound + 加真实 socks5 outbound (指向落地 IP)</li>
     *   <li>DB INSERT xray_client</li>
     * </ol>
     * 不重启 xray, 其他客户连接 0 感知.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public XrayClientDO provision(ClientProvisionReqVO reqVO) {
        // ① dup 检查 (软删行已被 @TableLogic 自动跳过, 复用流程不撞)
        XrayClientDO dup = xrayClientMapper.selectByMemberAndIp(reqVO.getMemberUserId(), reqVO.getIpId());
        if (ObjectUtil.isNotNull(dup)) {
            throw new BusinessException(XrayErrorCode.CLIENT_DUPLICATE,
                    "memberUserId=" + reqVO.getMemberUserId() + " ipId=" + reqVO.getIpId());
        }

        // ② 加载 server 端 xray 节点配置 (slot_pool_size / slot_port_base / xray_grpc_port)
        XrayNodeDO node = xrayNodeService.loadOrThrow(reqVO.getServerId());

        // ③ 加载落地 IP 凭据 (socks5 host/port/user/pass)
        IpPoolEntryDTO ipEntry = resourceIpPoolApi.loadEntry(reqVO.getIpId());
        if (StrUtil.isBlank(ipEntry.getSocks5Host()) || ObjectUtil.isNull(ipEntry.getSocks5Port())) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    reqVO.getIpId(), "落地 IP 的 SOCKS5 凭据未配置");
        }

        // ④ 准备 client 标识 + slot 占用 (要在 DB INSERT 前生成 id, 才能填 used_by)
        String clientId = UUID.randomUUID().toString().replace("-", "");
        int slotIndex = slotPoolService.allocate(reqVO.getServerId(), clientId);
        int listenPort = node.getSlotPortBase() + slotIndex;
        String inboundTag = formatSlotTag("in_slot_", slotIndex);
        String outboundTag = formatSlotTag("out_slot_", slotIndex);
        String clientUuid = UUID.randomUUID().toString();
        // email 格式: server 内全局唯一, 便于人肉对账与 stats key 索引
        String clientEmail = "member_" + reqVO.getMemberUserId() + "_" + reqVO.getIpId();

        InboundUserSpec userSpec = InboundUserSpec.builder()
                .externalInboundRef(inboundTag)
                .email(clientEmail)
                .uuid(clientUuid)
                .protocol(reqVO.getProtocol())
                .flow(StrUtil.blankToDefault(reqVO.getFlow(), ""))
                .totalBytes(reqVO.getTotalBytes() == null ? 0L : reqVO.getTotalBytes())
                .expiryEpochMillis(reqVO.getExpiryEpochMillis() == null ? 0L : reqVO.getExpiryEpochMillis())
                .limitIp(reqVO.getLimitIp() == null ? 0 : reqVO.getLimitIp())
                .build();

        try {
            // ⑤ 加 inbound (xray 进程内开始 listen :listenPort)
            inboundCli.addInbound(reqVO.getServerId(), inboundTag, listenPort, userSpec);

            // ⑥ 替换占位 freedom outbound 为真实 socks5
            // 删 + 加是非原子的, 中间有秒级窗口 outbound tag 不存在; 但 routing rule 是预置静态映射, 没匹配就走默认 outbound (freedom direct)
            // 这个窗口客户流量可能瞬时漏到 freedom direct (走 server 公网 IP 而非落地 IP), 时长 < 200ms
            // 真实秒级流量场景下基本不可见; 后续若担心, 可改为加新 outbound 后用 routing override 而非删占位
            outboundCli.removeOutbound(reqVO.getServerId(), outboundTag);
            outboundCli.addSocksOutbound(reqVO.getServerId(), outboundTag,
                    ipEntry.getSocks5Host(), ipEntry.getSocks5Port(),
                    ipEntry.getSocks5Username(), ipEntry.getSocks5Password());
        } catch (RuntimeException e) {
            // CLI 中途失败: 释放 slot, 让事务回滚 DB; 已 add 的 inbound/outbound 由后续巡检兜底
            log.error("[provision] CLI 失败 server={} slot={} email={}, 回滚 slot",
                    reqVO.getServerId(), slotIndex, clientEmail, e);
            try { inboundCli.removeInbound(reqVO.getServerId(), inboundTag); } catch (Exception ignore) { }
            try { outboundCli.removeOutbound(reqVO.getServerId(), outboundTag); } catch (Exception ignore) { }
            try { outboundCli.addFreedomOutbound(reqVO.getServerId(), outboundTag); } catch (Exception ignore) { }
            throw e;
        }

        // ⑦ DB INSERT
        XrayClientDO e = new XrayClientDO();
        e.setId(clientId);
        e.setServerId(reqVO.getServerId());
        e.setIpId(reqVO.getIpId());
        e.setMemberUserId(reqVO.getMemberUserId());
        e.setSlotIndex(slotIndex);
        e.setExternalInboundRef(inboundTag);
        e.setProtocol(reqVO.getProtocol());
        e.setTransport(StrUtil.blankToDefault(reqVO.getTransport(), "tcp"));
        e.setListenIp(StrUtil.blankToDefault(reqVO.getListenIp(), "0.0.0.0"));
        e.setListenPort(listenPort);
        e.setClientUuid(clientUuid);
        e.setClientEmail(clientEmail);
        e.setStatus(1);
        xrayClientMapper.insert(e);
        log.info("[provision] OK server={} slot={} port={} email={} ip={}",
                reqVO.getServerId(), slotIndex, listenPort, clientEmail, ipEntry.getSocks5Host());
        return e;
    }

    /**
     * 取消客户; 远端先删 inbound + outbound + 还原占位 freedom, 再软删 DB + 释放 slot.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        String inboundTag = e.getExternalInboundRef();
        String outboundTag = formatSlotTag("out_slot_", e.getSlotIndex());

        // 远端清理: 删 inbound (该客户连接断, 但其他客户不受影响)
        try {
            inboundCli.removeInbound(e.getServerId(), inboundTag);
        } catch (BusinessException be) {
            // 远端已不存在视为成功, 目标态本来就是没了
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
            log.warn("[revoke] inbound 已不存在 server={} tag={}", e.getServerId(), inboundTag);
        }

        // 删真实 socks5 outbound + 加回占位 freedom (保持 routing rule 引用不空)
        try {
            outboundCli.removeOutbound(e.getServerId(), outboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        try {
            outboundCli.addFreedomOutbound(e.getServerId(), outboundTag);
        } catch (RuntimeException re) {
            // 占位还原失败: 不阻塞 revoke 主流程, 仅 warn (routing rule 此时引用不存在的 outbound, 流量走 default)
            log.warn("[revoke] 占位 freedom 还原失败 server={} tag={}, 由后续巡检修复",
                    e.getServerId(), outboundTag, re);
        }

        // DB 软删 + slot 释放
        xrayClientMapper.deleteById(e.getId());
        slotPoolService.release(e.getServerId(), e.getSlotIndex());
        log.info("[revoke] OK server={} slot={} email={}",
                e.getServerId(), e.getSlotIndex(), e.getClientEmail());
    }

    /**
     * 换密钥 (UUID 轮换); 端口不变, inbound tag 不变, 重建 inbound 让新 UUID 生效.
     * 客户旧连接断 (~200ms), 用新 UUID 重连即可.
     */
    @Override
    public XrayClientDO rotate(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        String inboundTag = e.getExternalInboundRef();
        String newUuid = UUID.randomUUID().toString();

        // 删 inbound + 同 tag/port 重建 (1:1 模型每 inbound 1 user, rotate 走 inbound 重建)
        try {
            inboundCli.removeInbound(e.getServerId(), inboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }

        InboundUserSpec spec = InboundUserSpec.builder()
                .externalInboundRef(inboundTag)
                .email(e.getClientEmail())
                .uuid(newUuid)
                .protocol(e.getProtocol())
                .build();
        try {
            inboundCli.addInbound(e.getServerId(), inboundTag, e.getListenPort(), spec);
        } catch (RuntimeException addErr) {
            // 重建失败: 标 status=3 待巡检 — 客户连不上, 需要人工 / 后续 reconciler 介入
            log.error("[rotate] del 后 add 失败 server={} email={}, 标 status=3",
                    e.getServerId(), e.getClientEmail(), addErr);
            xrayClientMapper.updateStatus(e.getId(), 3, java.time.LocalDateTime.now());
            throw addErr;
        }

        xrayClientMapper.updateClientUuid(e.getId(), newUuid);
        e.setClientUuid(newUuid);
        log.info("[rotate] OK server={} slot={} email={} 新 UUID 已生效",
                e.getServerId(), e.getSlotIndex(), e.getClientEmail());
        return e;
    }

    @Override
    public ClientTrafficRespVO getTraffic(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        UserTraffic t = statsCli.readUserTraffic(e.getServerId(), e.getClientEmail(), false);
        return XrayClientConvert.INSTANCE.toTrafficVO(e, t);
    }

    @Override
    public void resetTraffic(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        // reset=true 原子返回旧值并清零
        statsCli.readUserTraffic(e.getServerId(), e.getClientEmail(), true);
    }

    @Override
    public XrayClientDO update(String inboundEntityId, ClientUpdateReqVO reqVO) {
        XrayClientDO e = findById(inboundEntityId);
        // 只允许改本地元数据; 不与远端同步 — 这些字段不影响远端 client 的实际行为
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
        // 客户连接的 host = server 公网 IP (resource_server.host); 拼订阅链接用
        vo.setServerHost(resourceServerApi.loadCredential(e.getServerId()).sshHost());
        vo.setListenPort(e.getListenPort());
        vo.setTransport(e.getTransport());
        return vo;
    }

    /** 把 slot 编号格式化成 "in_slot_05" / "out_slot_05" 这种 2 位补 0 字符串. */
    private static String formatSlotTag(String prefix, int slotIndex) {
        return prefix + String.format("%02d", slotIndex);
    }
}
