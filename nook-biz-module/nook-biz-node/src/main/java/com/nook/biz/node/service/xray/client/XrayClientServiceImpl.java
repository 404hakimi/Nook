package com.nook.biz.node.service.xray.client;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.convert.xray.client.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.cli.XrayInboundCli;
import com.nook.biz.node.framework.xray.cli.XrayOutboundCli;
import com.nook.biz.node.framework.xray.cli.XrayStatsCli;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.service.xray.slot.XraySlotPoolService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.resource.api.ResourceIpPoolApi;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.IpPoolEntryDTO;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class XrayClientServiceImpl implements XrayClientService {

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayInboundCli inboundCli;
    @Resource
    private XrayOutboundCli outboundCli;
    @Resource
    private XrayStatsCli statsCli;
    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private XraySlotPoolService slotPoolService;
    @Resource
    private ResourceServerApi resourceServerApi;
    @Resource
    private ResourceIpPoolApi resourceIpPoolApi;
    @Resource
    private XrayClientValidator clientValidator;

    @Override
    public XrayClientDO findById(String id) {
        return clientValidator.validateExists(id);
    }

    @Override
    public PageResult<XrayClientDO> page(ClientPageReqVO reqVO) {
        IPage<XrayClientDO> result = xrayClientMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public XrayClientDO provision(ClientProvisionReqVO reqVO) {
        // ① 业务校验: (memberUserId, ipId) 防重 (吊销走硬删, 旧行不存在 = 可重新 provision)
        clientValidator.validateNotDuplicate(reqVO.getMemberUserId(), reqVO.getIpId());

        // ② 加载 server 端 xray 节点配置 (slot_pool_size / slot_port_base / xray_api_port)
        XrayNodeDO node = xrayNodeService.loadOrThrow(reqVO.getServerId());

        // ③ 原子占用落地 IP (available → occupied) 并同时拿 socks5 凭据;
        // markOccupied 走 WHERE status=1, IP 已被占用时抛 IP_POOL_NOT_AVAILABLE.
        // 与下面 CLI 操作处于同一事务内, CLI 失败抛错时事务回滚 → IP 自动回到 available.
        IpPoolEntryDTO ipEntry = resourceIpPoolApi.occupyById(reqVO.getIpId(), reqVO.getMemberUserId());
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

        int apiPort = node.getXrayApiPort();
        // 防御性清零 stats counter: xray statsManager 按 email 索引 counter, 删 inbound 不会清,
        // 同 email 重新 provision 时新流量会累加到旧值上. 这里 reset=true 让新 client 必然从 0 起算.
        // 失败不阻断主流程 (新 email 时 counter 本就不存在 = no-op; SSH 不通后续 addInbound 也会抛错).
        try {
            statsCli.readUserTraffic(reqVO.getServerId(), apiPort, clientEmail, true);
        } catch (Exception ignore) { }

        // 用旗标精确追踪主流程完成度, catch 时只回滚已实际完成的步骤; 避免 addInbound 一开始就失败时
        // 错误地去删根本没碰过的 freedom 占位, 把远端状态搞脏.
        boolean inboundAdded = false;
        boolean freedomRemoved = false;
        boolean socksAdded = false;
        try {
            // ⑤ 加 inbound (xray 进程内开始 listen :listenPort)
            inboundCli.addInbound(reqVO.getServerId(), apiPort, inboundTag, listenPort, userSpec);
            inboundAdded = true;

            // ⑥ 替换占位 freedom outbound 为真实 socks5
            // 删 + 加是非原子的, 中间有秒级窗口 outbound tag 不存在; 但 routing rule 是预置静态映射, 没匹配就走默认 outbound (freedom direct)
            // 这个窗口客户流量可能瞬时漏到 freedom direct (走 server 公网 IP 而非落地 IP), 时长 < 200ms
            outboundCli.removeOutbound(reqVO.getServerId(), apiPort, outboundTag);
            freedomRemoved = true;

            outboundCli.addSocksOutbound(reqVO.getServerId(), apiPort, outboundTag,
                    ipEntry.getSocks5Host(), ipEntry.getSocks5Port(),
                    ipEntry.getSocks5Username(), ipEntry.getSocks5Password());
            socksAdded = true;
        } catch (RuntimeException e) {
            log.error("[provision] CLI 失败 server={} slot={} email={} stage=[inbound={} freedomRemoved={} socks={}], 按完成度回滚",
                    reqVO.getServerId(), slotIndex, clientEmail, inboundAdded, freedomRemoved, socksAdded, e);
            // 回滚顺序与正向相反; 每步独立 try-ignore, 单个失败不影响其他步骤. throw e 后事务回滚释放 slot 占用
            if (socksAdded) {
                try { outboundCli.removeOutbound(reqVO.getServerId(), apiPort, outboundTag); }
                catch (Exception ignore) { }
            }
            if (freedomRemoved) {
                // freedom 占位本来存在, 主流程把它删了但 socks 没加成 — 这里加回来保持 routing rule 引用不空
                try { outboundCli.addFreedomOutbound(reqVO.getServerId(), apiPort, outboundTag); }
                catch (Exception ignore) { }
            }
            if (inboundAdded) {
                try { inboundCli.removeInbound(reqVO.getServerId(), apiPort, inboundTag); }
                catch (Exception ignore) { }
            }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        String inboundTag = e.getExternalInboundRef();
        String outboundTag = formatSlotTag("out_slot_", e.getSlotIndex());
        int apiPort = xrayNodeService.loadOrThrow(e.getServerId()).getXrayApiPort();

        // 远端清理: 删 inbound (该客户连接断, 但其他客户不受影响)
        try {
            inboundCli.removeInbound(e.getServerId(), apiPort, inboundTag);
        } catch (BusinessException be) {
            // 远端已不存在视为成功, 目标态本来就是没了
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
            log.warn("[revoke] inbound 已不存在 server={} tag={}", e.getServerId(), inboundTag);
        }

        // 删真实 socks5 outbound + 加回占位 freedom (保持 routing rule 引用不空)
        try {
            outboundCli.removeOutbound(e.getServerId(), apiPort, outboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        try {
            outboundCli.addFreedomOutbound(e.getServerId(), apiPort, outboundTag);
        } catch (RuntimeException re) {
            // 占位还原失败: 不阻塞 revoke 主流程, 仅 warn (routing rule 此时引用不存在的 outbound, 流量走 default)
            log.warn("[revoke] 占位 freedom 还原失败 server={} tag={}, 由后续巡检修复",
                    e.getServerId(), outboundTag, re);
        }

        // DB 硬删 + slot 释放
        xrayClientMapper.deleteById(e.getId());
        slotPoolService.release(e.getServerId(), e.getSlotIndex());

        // 退订落地 IP: occupied → cooling, 等冷却到期由 sweep 任务回到 available;
        // 走 try 是因为 IP 行可能已被运维手动删 / 状态错位, 不阻断 revoke 主流程
        try {
            resourceIpPoolApi.releaseToCooling(e.getIpId());
        } catch (RuntimeException re) {
            log.warn("[revoke] IP 退订失败 server={} ipId={}, 需运维手动处理 IP 状态: {}",
                    e.getServerId(), e.getIpId(), re.getMessage());
        }

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
        int apiPort = xrayNodeService.loadOrThrow(e.getServerId()).getXrayApiPort();

        // 删 inbound + 同 tag/port 重建 (1:1 模型每 inbound 1 user, rotate 走 inbound 重建)
        try {
            inboundCli.removeInbound(e.getServerId(), apiPort, inboundTag);
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
            inboundCli.addInbound(e.getServerId(), apiPort, inboundTag, e.getListenPort(), spec);
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
        int apiPort = xrayNodeService.loadOrThrow(e.getServerId()).getXrayApiPort();
        XrayUserTrafficSnapshot t = statsCli.readUserTraffic(e.getServerId(), apiPort, e.getClientEmail(), false);
        return XrayClientConvert.INSTANCE.toTrafficVO(e, t);
    }

    @Override
    public void resetTraffic(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        int apiPort = xrayNodeService.loadOrThrow(e.getServerId()).getXrayApiPort();
        // reset=true 原子返回旧值并清零
        statsCli.readUserTraffic(e.getServerId(), apiPort, e.getClientEmail(), true);
    }

    @Override
    public void update(String inboundEntityId, ClientUpdateReqVO reqVO) {
        // 校验 client 存在
        clientValidator.validateExists(inboundEntityId);
        // 更新本地元数据; null 字段由 MP NOT_NULL 策略跳过, 即"保留原值"
        XrayClientDO entity = BeanUtils.toBean(reqVO, XrayClientDO.class);
        xrayClientMapper.update(entity, Wrappers.<XrayClientDO>lambdaUpdate()
                .eq(XrayClientDO::getId, inboundEntityId));
    }

    @Override
    public void enrichIpAddress(Collection<ClientRespVO> vos) {
        if (vos == null || vos.isEmpty()) return;
        // 收集 ipId 去重一次后批量查; 与上面 IP 池条目一对多关系无关, 这里只取地址展示用
        Set<String> ipIds = new HashSet<>();
        List<ClientRespVO> targets = new ArrayList<>(vos.size());
        for (ClientRespVO v : vos) {
            if (v == null) continue;
            targets.add(v);
            if (StrUtil.isNotBlank(v.getIpId())) ipIds.add(v.getIpId());
        }
        if (ipIds.isEmpty()) return;
        Map<String, String> idToAddr = resourceIpPoolApi.loadIpAddressMap(ipIds);
        for (ClientRespVO v : targets) {
            String addr = idToAddr.get(v.getIpId());
            if (addr != null) v.setIpAddress(addr);
        }
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
        vo.setServerHost(resourceServerApi.loadCredential(e.getServerId()).getSshHost());
        vo.setListenPort(e.getListenPort());
        vo.setTransport(e.getTransport());
        return vo;
    }

    /** 把 slot 编号格式化成 "in_slot_05" / "out_slot_05" 这种 2 位补 0 字符串. */
    private static String formatSlotTag(String prefix, int slotIndex) {
        return prefix + String.format("%02d", slotIndex);
    }
}
