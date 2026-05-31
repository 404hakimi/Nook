package com.nook.biz.node.event;

import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.system.api.region.event.RegionRecodedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 区域码更正监听: 把线路机 / 落地机 (resource_server.region) 的旧码迁到新码.
 *
 * <p>同步监听, 与 system 发布方同一事务; 抛异常即整体回滚.
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
        log.info("[区域更正] {} → {}: 迁移 {} 台机器", e.getOldCode(), e.getNewCode(), n);
    }
}
