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
 * 定时对账: 每日凌晨 3 点跑一轮; 通过 OperationOrchestrator 入队, 与用户手动操作自然 FIFO 互斥.
 *
 * <p>同 server 上若已有 RUNNING op (包括正在跑的 reconcile), DUPLICATE_OP 直接被吞 (catch 后跳过);
 * 用户操作密集时也不会卡占队列.
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
     * 每日 03:00; 单 server 失败不影响其他.
     */
    @Scheduled(cron = "${nook.reconciler.cron:0 0 3 * * ?}")
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
