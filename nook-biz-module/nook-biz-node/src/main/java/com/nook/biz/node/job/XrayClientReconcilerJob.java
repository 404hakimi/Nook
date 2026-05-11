package com.nook.biz.node.job;

import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.node.service.xray.client.XrayClientService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时对账: 每小时一轮, 仅探 xray 启动时间, 重启过才 replay; 1c2g 小机型友好.
 *
 * <p>平时单 server 仅 1 次 SSH (systemctl show xray); 检测到重启才走 ADI/RMI 全量推. 手动入口
 * (replayServer / syncOne) 不受这里影响, 运维任意时刻触发都行.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayClientReconcilerJob {

    @Resource
    private XrayNodeMapper xrayNodeMapper;
    @Resource
    private XrayClientService xrayClientService;

    /**
     * 1 小时一轮; 单 server 失败不影响其他.
     */
    @Scheduled(fixedDelayString = "${nook.reconciler.fixed-delay-ms:3600000}",
            initialDelayString = "${nook.reconciler.initial-delay-ms:300000}")
    public void reconcile() {
        List<XrayNodeDO> nodes = xrayNodeMapper.selectList(null);
        if (nodes == null || nodes.isEmpty()) return;

        log.debug("[reconciler] 启动轮询 size={}", nodes.size());
        for (XrayNodeDO node : nodes) {
            try {
                xrayClientService.replayIfRestarted(node.getServerId());
            } catch (Exception e) {
                log.error("[reconciler] server={} 出错: {}", node.getServerId(), e.getMessage());
            }
        }
    }
}
