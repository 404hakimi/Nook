package com.nook.biz.node.event;

import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.system.api.region.event.RegionRecodedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 区域码更正监听: 把线路机 / 落地机的区域码从旧码迁到新码; 同步监听与发布方同一事务, 抛异常整体回滚
 *
 * @author nook
 */
@Slf4j
@Component
public class NodeRegionRecodeListener {

    @Resource
    private ResourceServerMapper resourceServerMapper;

    @EventListener
    public void onRecode(RegionRecodedEvent e) {
        int n = resourceServerMapper.migrateRegion(e.getOldCode(), e.getNewCode());
        log.info("[onRecode] 区域码更正迁移: {} → {}, 迁移 {} 台机器", e.getOldCode(), e.getNewCode(), n);
    }
}
