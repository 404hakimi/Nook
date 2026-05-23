package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.controller.resource.vo.ResourceServerDnsUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDnsDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerDnsMapper;
import com.nook.biz.node.service.resource.ResourceServerDnsService;
import com.nook.biz.node.validator.ResourceServerDnsValidator;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 服务器 DNS 绑定 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerDnsServiceImpl implements ResourceServerDnsService {

    private final ResourceServerDnsMapper dnsMapper;
    private final ResourceServerDnsValidator dnsValidator;

    @Override
    public ResourceServerDnsDO get(String serverId) {
        return dnsMapper.selectById(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(String serverId, ResourceServerDnsUpdateReqVO reqVO) {
        dnsValidator.validateDomainUnique(null, reqVO == null ? null : reqVO.getDomain());
        ResourceServerDnsDO entity = reqVO == null
                ? new ResourceServerDnsDO()
                : BeanUtils.toBean(reqVO, ResourceServerDnsDO.class);
        entity.setServerId(serverId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        dnsMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String serverId, ResourceServerDnsUpdateReqVO reqVO) {
        dnsValidator.validateExists(serverId);
        dnsValidator.validateDomainUnique(serverId, reqVO.getDomain());
        ResourceServerDnsDO patch = BeanUtils.toBean(reqVO, ResourceServerDnsDO.class);
        patch.setServerId(serverId);
        dnsMapper.updateBySelective(patch);
    }
}
