package com.nook.biz.node.job;

import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.node.service.xray.client.XrayClientService;
import com.nook.biz.operation.enums.OpErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时对账: 默认 10 分钟一轮; 通过 OpOrchestrator 入队, 与用户手动操作自然 FIFO 互斥.
 *
 * <p>同 server 上若已有 RUNNING op (包括正在跑的 reconcile), DUPLICATE_OP 直接被吞 (catch 后跳过);
 * 用户操作密集时也不会卡占队列. 高频跑 + 内部只推 diff, 单 server 单轮代价≈1 次 SSH (lsi).
 *
 * <p>DB 是 source of truth, xray 内存态是派生; xray crash + systemd 拉起后所有客户 inbound 丢,
 * 本 Job 通过 systemd ActiveEnterTimestamp 检测重启, 自动 replay DB 全量推回去, 避免长时间断连.
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
     * 默认 10 分钟一轮; 单 server 失败不影响其他.
     */
    @Scheduled(cron = "${nook.reconciler.cron:0 */10 * * * ?}")
    public void reconcile() {
        List<XrayNodeDO> nodes = xrayNodeMapper.selectList(null);
        if (nodes == null || nodes.isEmpty()) return;

        log.debug("[reconciler] 启动轮询 size={}", nodes.size());
        for (XrayNodeDO node : nodes) {
            try {
                xrayClientService.replayIfRestarted(node.getServerId());
            } catch (BusinessException be) {
                // 上轮 op 还 RUNNING (用户在 provision / 上次 reconcile 没跑完); 正常去重, 不报错
                if (OpErrorCode.DUPLICATE_OP.getCode() == be.getCode()) {
                    log.debug("[reconciler] server={} 跳过 (已有 RUNNING op)", node.getServerId());
                } else {
                    log.error("[reconciler] server={} 业务异常: {}", node.getServerId(), be.getMessage());
                }
            } catch (Exception e) {
                log.error("[reconciler] server={} 出错: {}", node.getServerId(), e.getMessage());
            }
        }
    }
}
