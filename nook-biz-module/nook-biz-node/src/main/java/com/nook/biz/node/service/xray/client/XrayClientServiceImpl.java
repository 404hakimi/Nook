package com.nook.biz.node.service.xray.client;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.controller.xray.client.vo.ReplayReportRespVO;
import com.nook.biz.node.controller.xray.client.vo.SyncStatusRespVO;
import com.nook.biz.node.convert.xray.client.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.biz.node.framework.xray.cli.XrayInboundCli;
import com.nook.biz.node.framework.xray.cli.XrayOutboundCli;
import com.nook.biz.node.framework.xray.cli.XrayStatsCli;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.service.support.SessionCredentialMapper;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.service.xray.slot.XraySlotPoolService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.resource.api.ResourceIpPoolApi;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.IpPoolEntryDTO;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    @Resource
    private XrayDaemonProbe xrayDaemonProbe;
    @Resource
    private SessionCredentialMapper sessionCredentialMapper;

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
        // 业务校验
        clientValidator.validateForProvision(reqVO);
        clientValidator.validateNotDuplicate(reqVO.getMemberUserId(), reqVO.getIpId());

        // 加载 server xray 节点配置
        XrayNodeDO node = xrayNodeService.loadOrThrow(reqVO.getServerId());

        // 原子占用落地 IP, 同事务回滚自动归还
        IpPoolEntryDTO ipEntry = resourceIpPoolApi.occupyById(reqVO.getIpId(), reqVO.getMemberUserId());
        if (StrUtil.isBlank(ipEntry.getIpAddress()) || ObjectUtil.isNull(ipEntry.getSocks5Port())) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    reqVO.getIpId(), "落地 IP 的 SOCKS5 凭据未配置");
        }

        // 分配 client id + slot
        String clientId = UUID.randomUUID().toString().replace("-", "");
        int slotIndex = slotPoolService.allocate(reqVO.getServerId(), clientId);
        int listenPort = node.getSlotPortBase() + slotIndex;
        String inboundTag = formatSlotTag("in_slot_", slotIndex);
        String outboundTag = formatSlotTag("out_slot_", slotIndex);
        String clientUuid = UUID.randomUUID().toString();
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
        // 整个 provision 链路 acquire 一次, 所有 CLI 复用同一 session
        SshSession session = sessionCredentialMapper.acquire(reqVO.getServerId(), SshSessionScope.SHARED);
        // 防御性清零 stats counter, 让同 email 残留 (吊销失败 / 直删 DB 等) 路径下新 client 也从 0 起算
        try {
            statsCli.readUserTraffic(session, apiPort, clientEmail, true);
        } catch (Exception ignore) { }

        // 远端非事务, 用旗标精确回滚已完成的步骤
        boolean inboundAdded = false;
        boolean freedomRemoved = false;
        boolean socksAdded = false;
        try {
            inboundCli.addInbound(session, apiPort, inboundTag, listenPort, userSpec);
            inboundAdded = true;

            // 替换占位 freedom 为真实 socks; 删-加间隙 < 200ms, 期间漏到默认 outbound
            outboundCli.removeOutbound(session, apiPort, outboundTag);
            freedomRemoved = true;

            outboundCli.addSocksOutbound(session, apiPort, outboundTag,
                    ipEntry.getIpAddress(), ipEntry.getSocks5Port(),
                    ipEntry.getSocks5Username(), ipEntry.getSocks5Password());
            socksAdded = true;
        } catch (RuntimeException e) {
            log.error("[provision] CLI 失败 server={} slot={} email={} stage=[inbound={} freedomRemoved={} socks={}]",
                    reqVO.getServerId(), slotIndex, clientEmail, inboundAdded, freedomRemoved, socksAdded, e);
            if (socksAdded) {
                try { outboundCli.removeOutbound(session, apiPort, outboundTag); }
                catch (Exception ignore) { }
            }
            if (freedomRemoved) {
                try { outboundCli.addFreedomOutbound(session, apiPort, outboundTag); }
                catch (Exception ignore) { }
            }
            if (inboundAdded) {
                try { inboundCli.removeInbound(session, apiPort, inboundTag); }
                catch (Exception ignore) { }
            }
            throw e;
        }

        // DB INSERT
        XrayClientDO entity = XrayClientDO.builder()
                .id(clientId)
                .serverId(reqVO.getServerId())
                .ipId(reqVO.getIpId())
                .memberUserId(reqVO.getMemberUserId())
                .slotIndex(slotIndex)
                .externalInboundRef(inboundTag)
                .protocol(reqVO.getProtocol())
                .transport(reqVO.getTransport())
                .listenIp(reqVO.getListenIp())
                .listenPort(listenPort)
                .clientUuid(clientUuid)
                .clientEmail(clientEmail)
                .status(1)
                .build();
        xrayClientMapper.insert(entity);
        log.info("[provision] OK server={} slot={} port={} email={} ip={}",
                reqVO.getServerId(), slotIndex, listenPort, clientEmail, ipEntry.getIpAddress());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        String inboundTag = e.getExternalInboundRef();
        String outboundTag = formatSlotTag("out_slot_", e.getSlotIndex());
        int apiPort = xrayNodeService.loadOrThrow(e.getServerId()).getXrayApiPort();
        SshSession session = sessionCredentialMapper.acquire(e.getServerId(), SshSessionScope.SHARED);

        // 远端清理: 删 inbound (该客户连接断, 但其他客户不受影响)
        try {
            inboundCli.removeInbound(session, apiPort, inboundTag);
        } catch (BusinessException be) {
            // 远端已不存在视为成功, 目标态本来就是没了
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
            log.warn("[revoke] inbound 已不存在 server={} tag={}", e.getServerId(), inboundTag);
        }

        // 删真实 socks5 outbound + 加回占位 freedom (保持 routing rule 引用不空)
        try {
            outboundCli.removeOutbound(session, apiPort, outboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        try {
            outboundCli.addFreedomOutbound(session, apiPort, outboundTag);
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
        SshSession session = sessionCredentialMapper.acquire(e.getServerId(), SshSessionScope.SHARED);

        // 删 inbound + 同 tag/port 重建 (1:1 模型每 inbound 1 user, rotate 走 inbound 重建)
        try {
            inboundCli.removeInbound(session, apiPort, inboundTag);
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
            inboundCli.addInbound(session, apiPort, inboundTag, e.getListenPort(), spec);
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
        XrayClientDO xrayClient = findById(inboundEntityId);
        int apiPort = xrayNodeService.loadOrThrow(xrayClient.getServerId()).getXrayApiPort();
        SshSession session = sessionCredentialMapper.acquire(xrayClient.getServerId(), SshSessionScope.SHARED);
        XrayUserTrafficSnapshot traffic = statsCli.readUserTraffic(session, apiPort, xrayClient.getClientEmail(), false);
        return XrayClientConvert.INSTANCE.toTrafficVO(xrayClient, traffic);
    }

    @Override
    public void resetTraffic(String inboundEntityId) {
        XrayClientDO e = findById(inboundEntityId);
        int apiPort = xrayNodeService.loadOrThrow(e.getServerId()).getXrayApiPort();
        SshSession session = sessionCredentialMapper.acquire(e.getServerId(), SshSessionScope.SHARED);
        // reset=true 原子返回旧值并清零
        statsCli.readUserTraffic(session, apiPort, e.getClientEmail(), true);
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

    @Override
    public SyncStatusRespVO getSyncStatus(String serverId) {
        SyncStatusRespVO vo = new SyncStatusRespVO();
        vo.setServerId(serverId);
        vo.setOkTags(Collections.emptyList());
        vo.setStaleDbTags(Collections.emptyList());
        vo.setOrphanRemoteTags(Collections.emptyList());

        SshSession session;
        int apiPort;
        try {
            apiPort = xrayNodeService.loadOrThrow(serverId).getXrayApiPort();
            session = sessionCredentialMapper.acquire(serverId, SshSessionScope.RECONCILE);
        } catch (RuntimeException e) {
            vo.setReachable(false);
            log.warn("[reconciler] getSyncStatus 不可达 server={}: {}", serverId, e.getMessage());
            return vo;
        }
        vo.setReachable(true);

        // 远端 inbound list; 过滤掉静态预置 (config.json 里 dokodemo "api" inbound)
        Set<String> remote = new HashSet<>(inboundCli.listInbounds(session, apiPort));
        remote.remove("api");

        // DB 里 server 关联的活动 client (status != 2 已停)
        Set<String> dbTags = new HashSet<>();
        for (XrayClientDO c : xrayClientMapper.selectByServerId(serverId)) {
            if (c.getStatus() != null && c.getStatus() == 2) continue;
            if (StrUtil.isNotBlank(c.getExternalInboundRef())) dbTags.add(c.getExternalInboundRef());
        }

        List<String> ok = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String tag : dbTags) {
            if (remote.contains(tag)) ok.add(tag);
            else stale.add(tag);
        }
        List<String> orphan = new ArrayList<>(remote);
        orphan.removeAll(dbTags);

        vo.setOkTags(ok);
        vo.setStaleDbTags(stale);
        vo.setOrphanRemoteTags(orphan);
        return vo;
    }

    @Override
    public void syncOne(String clientId) {
        XrayClientDO c = clientValidator.validateExists(clientId);
        XrayNodeDO node = xrayNodeService.loadOrThrow(c.getServerId());
        SshSession session = sessionCredentialMapper.acquire(c.getServerId(), SshSessionScope.RECONCILE);
        IpPoolEntryDTO ipEntry = resourceIpPoolApi.loadEntry(c.getIpId());
        syncSingle(session, node.getXrayApiPort(), c, ipEntry);
    }

    @Override
    public ReplayReportRespVO replayServer(String serverId) {
        XrayNodeDO node = xrayNodeService.loadOrThrow(serverId);
        SshSession session = sessionCredentialMapper.acquire(serverId, SshSessionScope.RECONCILE);
        return replayInternal(session, node);
    }

    @Override
    public void replayIfRestarted(String serverId) {
        XrayNodeDO node = xrayNodeService.loadOrThrow(serverId);
        SshSession session;
        try {
            session = sessionCredentialMapper.acquire(serverId, SshSessionScope.RECONCILE);
        } catch (RuntimeException e) {
            log.warn("[reconciler] SSH 不通 server={}, 本轮跳过: {}", serverId, e.getMessage());
            return;
        }
        Optional<Instant> currentUptime = xrayDaemonProbe.readUptime(session);
        if (currentUptime.isEmpty()) {
            // xray 没起 / unit 缺失; 不能判断重启, 跳过, 等下一轮
            return;
        }
        Instant cur = currentUptime.get();
        Instant last = node.getLastXrayUptime() == null
                ? null : node.getLastXrayUptime().toInstant(ZoneOffset.UTC);
        // last 为 null (首次) 也视为重启; xray 装好后第一轮自动 replay 一次把 DB 推全
        if (last != null && !cur.isAfter(last)) return;

        log.info("[reconciler] xray 重启检测 server={} prev={} now={}, 触发 replay", serverId, last, cur);
        replayInternal(session, node);
        xrayNodeService.markReplayDone(serverId, LocalDateTime.ofInstant(cur, ZoneOffset.UTC));
    }

    /**
     * replay 核心: 先 lsi 拿远端 inbound tag, 仅推 "DB 有但远端缺" 的 client, 已对齐的跳过避免断连;
     * reconciler 调度 / 手动全量 replay 共用. 一次 acquire 一次拉表, 一次 lsi.
     */
    private ReplayReportRespVO replayInternal(SshSession session, XrayNodeDO node) {
        String serverId = node.getServerId();
        int apiPort = node.getXrayApiPort();

        List<XrayClientDO> targets = new ArrayList<>();
        for (XrayClientDO c : xrayClientMapper.selectByServerId(serverId)) {
            if (c.getStatus() != null && c.getStatus() == 2) continue;
            targets.add(c);
        }

        // 拉远端 inbound tag set (1 次 SSH); 远端已存在的 inbound 视为客户在用, 跳过避免无谓断连.
        // 1:1 模型 outbound tag 跟 inbound tag 一一对应 + provision 时同步替换, 所以 inbound 在 = 整套 OK.
        Set<String> remoteTags = new HashSet<>(inboundCli.listInbounds(session, apiPort));

        List<XrayClientDO> needSync = new ArrayList<>();
        for (XrayClientDO c : targets) {
            String tag = c.getExternalInboundRef();
            if (StrUtil.isNotBlank(tag) && !remoteTags.contains(tag)) {
                needSync.add(c);
            }
        }

        // 仅对 needSync 准备 IP 凭据; 已对齐的不查省 DB 调用
        Map<String, IpPoolEntryDTO> ipMap = new HashMap<>(needSync.size() * 2);
        for (XrayClientDO c : needSync) {
            String ipId = c.getIpId();
            if (StrUtil.isBlank(ipId) || ipMap.containsKey(ipId)) continue;
            try {
                ipMap.put(ipId, resourceIpPoolApi.loadEntry(ipId));
            } catch (Exception ex) {
                ipMap.put(ipId, null);
            }
        }

        int success = 0;
        List<String> failed = new ArrayList<>();
        for (XrayClientDO c : needSync) {
            try {
                syncSingle(session, apiPort, c, ipMap.get(c.getIpId()));
                success++;
            } catch (Exception ex) {
                log.error("[reconciler] sync 失败 client={} email={}: {}",
                        c.getId(), c.getClientEmail(), ex.getMessage());
                failed.add(c.getId());
            }
        }

        int alreadyOk = targets.size() - needSync.size();
        ReplayReportRespVO report = new ReplayReportRespVO();
        report.setServerId(serverId);
        report.setTotalCount(targets.size());
        report.setAlreadyOkCount(alreadyOk);
        report.setSuccessCount(success);
        report.setFailedClientIds(failed);
        log.info("[reconciler] replay server={} total={} alreadyOk={} synced={} failed={}",
                serverId, targets.size(), alreadyOk, success, failed.size());
        return report;
    }

    /**
     * 单 client 同步到远端 (幂等 remove → add); 入参全部 caller 预查好, 内部仅调 CLI + 1 次 status 写回.
     */
    private void syncSingle(SshSession session, int apiPort, XrayClientDO c, IpPoolEntryDTO ipEntry) {
        if (ipEntry == null || StrUtil.isBlank(ipEntry.getIpAddress()) || ObjectUtil.isNull(ipEntry.getSocks5Port())) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    c.getIpId(), "落地 IP 凭据丢失, 无法 sync");
        }

        String inboundTag = c.getExternalInboundRef();
        String outboundTag = formatSlotTag("out_slot_", c.getSlotIndex());

        // inbound 重建 (幂等)
        try {
            inboundCli.removeInbound(session, apiPort, inboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        InboundUserSpec spec = InboundUserSpec.builder()
                .externalInboundRef(inboundTag)
                .email(c.getClientEmail())
                .uuid(c.getClientUuid())
                .protocol(c.getProtocol())
                .build();
        try {
            inboundCli.addInbound(session, apiPort, inboundTag, c.getListenPort(), spec);
        } catch (RuntimeException addErr) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw addErr;
        }

        // outbound 重建 (幂等)
        try {
            outboundCli.removeOutbound(session, apiPort, outboundTag);
        } catch (BusinessException be) {
            if (XrayErrorCode.CLIENT_NOT_FOUND.getCode() != be.getCode()) throw be;
        }
        try {
            outboundCli.addSocksOutbound(session, apiPort, outboundTag,
                    ipEntry.getIpAddress(), ipEntry.getSocks5Port(),
                    ipEntry.getSocks5Username(), ipEntry.getSocks5Password());
        } catch (RuntimeException addErr) {
            xrayClientMapper.updateStatus(c.getId(), 3, LocalDateTime.now());
            throw addErr;
        }

        xrayClientMapper.updateStatus(c.getId(), 1, LocalDateTime.now());
    }

    /** 把 slot 编号格式化成 "in_slot_05" / "out_slot_05" 这种 2 位补 0 字符串. */
    private static String formatSlotTag(String prefix, int slotIndex) {
        return prefix + String.format("%02d", slotIndex);
    }
}
