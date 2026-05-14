package com.nook.biz.node.service.xray.client;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper.TrafficCounterRow;
import com.nook.biz.node.framework.xray.cli.XrayStatsCli;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Xray 客户端流量采样 Service 实现类.
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayClientTrafficSampleServiceImpl implements XrayClientTrafficSampleService {

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayClientTrafficMapper xrayClientTrafficMapper;
    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private XrayStatsCli xrayStatsCli;

    @Override
    public SampleStat sampleServerTraffic(String serverId) {
        if (StrUtil.isBlank(serverId)) return SampleStat.EMPTY;
        // server 尚未装 xray (无 xray_node 行) 不算采样失败, 静默跳过
        XrayNodeDO node = xrayNodeService.getXrayNode(serverId);
        if (node == null) {
            log.debug("[traffic-sample] 服务器={} 无 xray 节点记录, 跳过", serverId);
            return SampleStat.EMPTY;
        }
        return sampleServerTraffic(node);
    }

    @Override
    public SampleStat sampleServerTraffic(XrayNodeDO node) {
        if (node == null || StrUtil.isBlank(node.getServerId())) return SampleStat.EMPTY;
        String serverId = node.getServerId();

        // ① 拉远端内存计数器的当前累计值 (不清零); 增量由 DB 用"当前值 - 上次值"算
        Map<String, XrayUserTrafficSnapshot> counters;
        try {
            SshSession session = SshSessions.acquire(serverId, SshSessionScope.RECONCILE);
            counters = xrayStatsCli.readAllUserTraffics(session, node.getXrayApiPort(), false);
        } catch (RuntimeException e) {
            log.warn("[traffic-sample] 服务器={} SSH 拉计数器失败, 跳过本轮: {}", serverId, e.getMessage());
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

        // ③ 把当前累计值打包成一批 row, 一条 SQL upsert; SQL 内 CASE 算"这次增量"再累加
        LocalDateTime now = LocalDateTime.now();
        List<TrafficCounterRow> rows = new ArrayList<>(counters.size());
        int skipped = 0;
        for (XrayUserTrafficSnapshot snap : counters.values()) {
            String clientId = emailToClientId.get(snap.getEmail());
            if (clientId == null) {
                // 远端有 DB 无 → 孤儿 inbound / DB 被直删, 留给 reconciler 处理
                log.debug("[traffic-sample] 服务器={} 孤儿邮箱={}", serverId, snap.getEmail());
                skipped++;
                continue;
            }
            long curUp = Math.max(0L, snap.getUpBytes());
            long curDown = Math.max(0L, snap.getDownBytes());
            rows.add(new TrafficCounterRow(
                    UUID.randomUUID().toString().replace("-", ""),
                    clientId, serverId, curUp, curDown, now));
        }
        if (rows.isEmpty()) return new SampleStat(0, skipped);
        try {
            xrayClientTrafficMapper.batchUpsertCounter(rows);
        } catch (Exception e) {
            // 整批失败不丢数据: 远端计数器没被清零, 下一轮采样拿到的当前值跟这次接近,
            // SQL 内"当前值 - 上次值"会把本轮 + 下轮的完整增量一起算出来
            log.warn("[traffic-sample] 批量入库失败 服务器={} 条数={}: {}",
                    serverId, rows.size(), e.getMessage());
            return new SampleStat(0, skipped);
        }
        return new SampleStat(rows.size(), skipped);
    }

}
