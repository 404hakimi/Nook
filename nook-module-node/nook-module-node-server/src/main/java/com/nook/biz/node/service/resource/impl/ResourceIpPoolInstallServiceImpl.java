package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolInstallDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolInstallMapper;
import com.nook.biz.node.service.resource.ResourceIpPoolInstallService;
import com.nook.common.utils.collection.CollectionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * SOCKS5 装机事实 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceIpPoolInstallServiceImpl implements ResourceIpPoolInstallService {

    private final ResourceIpPoolInstallMapper installMapper;

    @Override
    public void upsert(ResourceIpPoolInstallDO entity) {
        ResourceIpPoolInstallDO exist = installMapper.selectById(entity.getIpId());
        if (exist == null) {
            installMapper.insert(entity);
        } else {
            installMapper.updateById(entity);
        }
    }

    @Override
    public ResourceIpPoolInstallDO get(String ipId) {
        return installMapper.selectById(ipId);
    }

    @Override
    public Map<String, ResourceIpPoolInstallDO> listByIpIds(Collection<String> ipIds) {
        if (ipIds == null || ipIds.isEmpty()) return Map.of();
        return CollectionUtils.convertMap(installMapper.selectByIpIds(ipIds), ResourceIpPoolInstallDO::getIpId);
    }

    @Override
    public void updateDanteUptime(String ipId, LocalDateTime uptime) {
        if (ObjectUtil.isNull(uptime)) return;
        installMapper.updateLastDanteUptime(ipId, uptime);
    }
}
