package com.nook.biz.node.service.xray.config.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.mysql.mapper.XrayConfigMapper;
import com.nook.biz.node.service.xray.config.XrayConfigService;
import com.nook.common.utils.collection.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Xray inbound 共享配置 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XrayConfigServiceImpl implements XrayConfigService {

    private final XrayConfigMapper xrayConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(XrayConfigDO entity) {
        XrayConfigDO existing = xrayConfigMapper.selectById(entity.getServerId());
        if (ObjectUtil.isNull(existing)) {
            xrayConfigMapper.insert(entity);
        } else {
            xrayConfigMapper.updateById(entity);
        }
        log.info("[xray-config] upsert server={} protocol={} port={} domain={}",
                entity.getServerId(), entity.getProtocol(), entity.getSharedInboundPort(), entity.getDomain());
    }

    @Override
    public XrayConfigDO get(String serverId) {
        return xrayConfigMapper.selectById(serverId);
    }

    @Override
    public Map<String, XrayConfigDO> listByServerIds(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) return Collections.emptyMap();
        return CollectionUtils.convertMap(
                xrayConfigMapper.selectBatchIds(serverIds), XrayConfigDO::getServerId);
    }
}
