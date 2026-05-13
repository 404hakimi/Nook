package com.nook.biz.node.service.xray.client;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.client.XrayClientTrafficDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper.TrafficDeltaRow;
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
import java.util.ArrayList;
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
    public SampleStat sampleServerTraffic(String serverId) {
        if (StrUtil.isBlank(serverId)) return SampleStat.EMPTY;
        try {
            return sampleServerTraffic(xrayNodeService.getXrayNode(serverId));
        } catch (RuntimeException e) {
            // server 尚未装 xray (无 xray_node 行), 不算 sample 失败
            log.debug("[traffic-sample] server={} 无 xray_node 记录, 跳过", serverId);
            return SampleStat.EMPTY;
        }
    }

    @Override
    public SampleStat sampleServerTraffic(XrayNodeDO node) {
        if (node == null || StrUtil.isBlank(node.getServerId())) return SampleStat.EMPTY;
        String serverId = node.getServerId();

        // ① 拉远端 in-memory counter; reset=true 清零让下一周期重新累加
        Map<String, XrayUserTrafficSnapshot> counters;
        try {
            SshSession session = sessionCredentialMapper.acquire(serverId, SshSessionScope.RECONCILE);
            counters = xrayStatsCli.readAllUserTraffics(session, node.getXrayApiPort(), true);
        } catch (RuntimeException e) {
            log.warn("[traffic-sample] server={} SSH 取 counter 失败, 跳过本轮: {}", serverId, e.getMessage());
            return SampleStat.EMPTY;
        }
        if (counters == null || counters.isEmpty()) return SampleStat.EMPTY;

        // ② email → clientId 映射 (含已停用 client, 防 email 匹配漏掉残留 counter)
        Map<String, String> emailToClientId = CollectionUtils.convertMap(
                CollectionUtils.filterList(
                        xrayClientMapper.selectByServerId(serverId),
                        c -> StrUtil.isNotBlank(c.getClientEmail())),
                XrayClientDO::getClientEmail,
                XrayClientDO::getId);

        // ③ 把增量打包成一批 row, 1 条 SQL upsert (N 客户从 N 次 round-trip 降到 1)
        LocalDateTime now = LocalDateTime.now();
        List<TrafficDeltaRow> rows = new ArrayList<>(counters.size());
        int skipped = 0;
        for (XrayUserTrafficSnapshot snap : counters.values()) {
            String clientId = emailToClientId.get(snap.getEmail());
            if (clientId == null) {
                // 远端有 DB 无 → 孤儿 inbound / DB 被直删, 留给 reconciler 处理
                skipped++;
                continue;
            }
            long deltaUp = Math.max(0L, snap.getUpBytes());
            long deltaDown = Math.max(0L, snap.getDownBytes());
            if (deltaUp == 0 && deltaDown == 0) continue;
            rows.add(new TrafficDeltaRow(
                    UUID.randomUUID().toString().replace("-", ""),
                    clientId, serverId, deltaUp, deltaDown, now));
        }
        if (rows.isEmpty()) return new SampleStat(0, skipped);
        try {
            xrayClientTrafficMapper.batchUpsertDelta(rows);
        } catch (Exception e) {
            // 整批失败丢一轮, 等下一周期重试; 不做单条降级 (并发安全 + 简化)
            log.warn("[traffic-sample] batch upsert 失败 server={} rows={}: {}",
                    serverId, rows.size(), e.getMessage());
            return new SampleStat(0, skipped);
        }
        return new SampleStat(rows.size(), skipped);
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
}
