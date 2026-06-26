package com.nook.biz.node.service.xray.server.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.entity.XrayTlsCertDO;
import com.nook.biz.node.mapper.XrayTlsCertMapper;
import com.nook.biz.node.service.xray.server.XrayTlsCertService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Xray TLS 证书 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayTlsCertServiceImpl implements XrayTlsCertService {

    @Resource
    private XrayTlsCertMapper xrayTlsCertMapper;

    @Override
    public XrayTlsCertDO get(String serverId) {
        return xrayTlsCertMapper.selectById(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(String serverId, String fqdn, String certPem, String keyPem, LocalDateTime notAfter) {
        XrayTlsCertDO row = new XrayTlsCertDO();
        row.setServerId(serverId);
        row.setFqdn(fqdn);
        row.setCertPem(certPem);
        row.setKeyPem(keyPem);
        row.setNotAfter(notAfter);
        if (ObjectUtil.isNull(xrayTlsCertMapper.selectById(serverId))) {
            xrayTlsCertMapper.insert(row);
        } else {
            xrayTlsCertMapper.updateById(row);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear(String serverId) {
        xrayTlsCertMapper.deleteById(serverId);
    }

    @Override
    public List<XrayTlsCertDO> listExpiring(LocalDateTime before) {
        return xrayTlsCertMapper.selectExpiringBefore(before);
    }
}
