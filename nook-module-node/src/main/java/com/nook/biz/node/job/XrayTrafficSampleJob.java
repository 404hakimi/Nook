package com.nook.biz.node.job;

import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService.SampleStat;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Xray 用户流量定时采样作业
 *
 * @author nook
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nook.traffic.enabled", havingValue = "true", matchIfMissing = true)
public class XrayTrafficSampleJob {

    @Resource
    private XrayNodeMapper xrayNodeMapper;
    @Resource
    private XrayClientTrafficSampleService xrayClientTrafficSampleService;

    // fixedDelay: 上一轮跑完后再等下一轮, 避免重叠堆积; initialDelay 给 xray_node 缓存 / SSH manager 留启动时间
    @Scheduled(
            fixedDelayString = "${nook.traffic.sample-interval-ms:1800000}",
            initialDelayString = "${nook.traffic.initial-delay-ms:60000}")
    public void sweepAllServers() {
        List<XrayNodeDO> nodes = xrayNodeMapper.selectList(null);
        if (nodes == null || nodes.isEmpty()) return;

        long t0 = System.currentTimeMillis();
        int okServers = 0;
        int failedServers = 0;
        int totalUpserted = 0;
        int totalSkipped = 0;
        for (XrayNodeDO node : nodes) {
            try {
                // 直接传 node, 服务侧不再按 serverId 重查; 节省 N 次 selectById
                SampleStat stat = xrayClientTrafficSampleService.sampleServerTraffic(node);
                okServers++;
                totalUpserted += stat.upserted();
                totalSkipped += stat.skipped();
            } catch (Exception e) {
                // 单 server 异常不阻塞其他
                failedServers++;
                log.warn("[traffic-sample] 服务器={} 采样异常: {}", node.getServerId(), e.getMessage());
            }
        }
        log.info("[traffic-sample] 采样完成 xray节点={} 成功={} 失败={} 入库={} 跳过={} 耗时={}ms",
                nodes.size(), okServers, failedServers, totalUpserted, totalSkipped,
                System.currentTimeMillis() - t0);
    }
}
