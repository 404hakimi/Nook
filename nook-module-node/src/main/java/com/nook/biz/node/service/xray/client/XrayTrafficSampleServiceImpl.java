package com.nook.biz.node.service.xray.client;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.client.XrayClientTrafficDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper;
import com.nook.biz.node.framework.xray.cli.XrayStatsCli;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.service.support.SessionCredentialMapper;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Xray 用户流量采样 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayTrafficSampleServiceImpl implements XrayTrafficSampleService {

    private static final String STATS_USER_PATTERN = "user>>>";

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayClientTrafficMapper xrayClientTrafficMapper;
    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private XrayStatsCli xrayStatsCli;
    @Resource
    private SessionCredentialMapper sessionCredentialMapper;
    @Resource
    private XrayClientValidator xrayClientValidator;

    @Override
    public void sampleServerTraffic(String serverId) {
        if (StrUtil.isBlank(serverId)) {
            return;
        }
        XrayNodeDO node = loadNodeOrNull(serverId);
        if (node == null) {
            return;
        }
        // reset=true 把 counter 清零, 增量落到 DB 后下一周期重新从 0 累加
        Map<String, XrayUserTrafficSnapshot> remoteCounters = readRemoteCounters(serverId, node.getXrayApiPort());
        if (remoteCounters == null || remoteCounters.isEmpty()) {
            return;
        }
        Map<String, String> emailToClientId = buildEmailToClientIdMap(serverId);
        upsertTrafficDeltas(serverId, remoteCounters, emailToClientId);
    }

    @Override
    public XrayUserTrafficSnapshot getTotalTraffic(String clientId) {
        XrayClientDO client = xrayClientValidator.validateExists(clientId);
        XrayClientTrafficDO row = xrayClientTrafficMapper.selectByClientId(clientId);
        long dbUp = row == null || row.getUplinkBytes() == null ? 0L : row.getUplinkBytes();
        long dbDown = row == null || row.getDownlinkBytes() == null ? 0L : row.getDownlinkBytes();

        long liveUp = 0L;
        long liveDown = 0L;
        boolean live = true;
        try {
            // 不 reset, 留给下一轮定时 sample
            XrayNodeDO node = xrayNodeService.getXrayNode(client.getServerId());
            SshSession session = sessionCredentialMapper.acquire(client.getServerId(), SshSessionScope.SHARED);
            XrayUserTrafficSnapshot snap = xrayStatsCli.readUserTraffic(session, node.getXrayApiPort(),
                    client.getClientEmail(), false);
            liveUp = snap.getUpBytes();
            liveDown = snap.getDownBytes();
        } catch (RuntimeException ex) {
            // SSH 不通仅返 DB 部分, enabled=false 让 UI 标"数据不完整"
            log.warn("[traffic-sample] getTotalTraffic SSH 失败 client={} email={}: {}",
                    clientId, client.getClientEmail(), ex.getMessage());
            live = false;
        }
        return new XrayUserTrafficSnapshot(
                client.getClientEmail(),
                dbUp + liveUp,
                dbDown + liveDown,
                0L,
                0L,
                live);
    }

    private XrayNodeDO loadNodeOrNull(String serverId) {
        try {
            return xrayNodeService.getXrayNode(serverId);
        } catch (RuntimeException e) {
            // server 尚未装 xray, 不算 sample 失败
            log.debug("[traffic-sample] server={} 无 xray_node 记录, 跳过", serverId);
            return null;
        }
    }

    private Map<String, XrayUserTrafficSnapshot> readRemoteCounters(String serverId, int xrayApiPort) {
        try {
            SshSession session = sessionCredentialMapper.acquire(serverId, SshSessionScope.RECONCILE);
            return xrayStatsCli.readUserTraffics(session, xrayApiPort, STATS_USER_PATTERN, true);
        } catch (RuntimeException e) {
            log.warn("[traffic-sample] server={} SSH 取 counter 失败, 跳过本轮: {}", serverId, e.getMessage());
            return null;
        }
    }

    private Map<String, String> buildEmailToClientIdMap(String serverId) {
        // 含已停用 client, 防 email 匹配漏掉残留 counter
        List<XrayClientDO> clients = xrayClientMapper.selectByServerId(serverId);
        List<XrayClientDO> withEmail = CollectionUtils.filterList(clients,
                c -> StrUtil.isNotBlank(c.getClientEmail()));
        return CollectionUtils.convertMap(withEmail,
                XrayClientDO::getClientEmail,
                XrayClientDO::getId);
    }

    private void upsertTrafficDeltas(String serverId,
                                     Map<String, XrayUserTrafficSnapshot> remoteCounters,
                                     Map<String, String> emailToClientId) {
        LocalDateTime now = LocalDateTime.now();
        int upserted = 0;
        int skipped = 0;
        for (Map.Entry<String, XrayUserTrafficSnapshot> entry : remoteCounters.entrySet()) {
            XrayUserTrafficSnapshot snap = entry.getValue();
            String clientId = emailToClientId.get(snap.getEmail());
            if (clientId == null) {
                // 远端有 DB 无 → 孤儿 inbound / DB 被直删, 留给 reconciler 处理
                skipped++;
                continue;
            }
            long deltaUp = Math.max(0L, snap.getUpBytes());
            long deltaDown = Math.max(0L, snap.getDownBytes());
            if (deltaUp == 0 && deltaDown == 0) {
                continue;
            }
            try {
                // id 走 UUID, ON DUPLICATE 走 UPDATE 时不覆盖原 id
                xrayClientTrafficMapper.upsertDelta(
                        UUID.randomUUID().toString().replace("-", ""),
                        clientId, serverId, deltaUp, deltaDown, now);
                upserted++;
            } catch (Exception ex) {
                log.warn("[traffic-sample] upsert 失败 server={} client={} email={}: {}",
                        serverId, clientId, snap.getEmail(), ex.getMessage());
            }
        }
        log.debug("[traffic-sample] server={} 已采样 upserted={} skipped={}", serverId, upserted, skipped);
    }
}
