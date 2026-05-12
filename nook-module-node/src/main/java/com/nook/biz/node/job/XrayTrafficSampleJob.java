package com.nook.biz.node.job;

import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.node.service.xray.client.XrayTrafficSampleService;
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
    private XrayTrafficSampleService xrayTrafficSampleService;

    // fixedDelay: 上一轮跑完后再等下一轮, 避免重叠堆积; initialDelay 给 xray_node 缓存 / SSH manager 留启动时间
    @Scheduled(
            fixedDelayString = "${nook.traffic.sample-interval-ms:1800000}",
            initialDelayString = "${nook.traffic.initial-delay-ms:60000}")
    public void sweepAllServers() {
        List<XrayNodeDO> nodes = xrayNodeMapper.selectList(null);
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        log.debug("[traffic-sample] sweep 启动 size={}", nodes.size());
        for (XrayNodeDO node : nodes) {
            try {
                xrayTrafficSampleService.sampleServerTraffic(node.getServerId());
            } catch (Exception e) {
                // 单 server 失败不阻塞其他
                log.warn("[traffic-sample] server={} 采样异常: {}", node.getServerId(), e.getMessage());
            }
        }
    }
}
