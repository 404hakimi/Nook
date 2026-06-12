package com.nook.biz.node.service.xray.config.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.mapper.XrayInboundMapper;
import com.nook.biz.node.service.xray.config.XrayInboundService;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;

/**
 * Xray inbound 共享配置 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayInboundServiceImpl implements XrayInboundService {

    @Resource
    private XrayInboundMapper xrayInboundMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(XrayInboundDO entity) {
        XrayInboundDO existing = xrayInboundMapper.selectById(entity.getServerId());
        if (ObjectUtil.isNull(existing)) {
            xrayInboundMapper.insert(entity);
        } else {
            xrayInboundMapper.updateById(entity);
        }
        log.info("[xray-inbound] upsert server={} protocol={} port={} domain={}",
                entity.getServerId(), entity.getProtocol(), entity.getSharedInboundPort(), entity.getDomain());
    }

    @Override
    public XrayInboundDO get(String serverId) {
        return xrayInboundMapper.selectById(serverId);
    }

    @Override
    public Map<String, XrayInboundDO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                xrayInboundMapper.selectBatchIds(serverIds), XrayInboundDO::getServerId);
    }
}
