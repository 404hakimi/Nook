package com.nook.biz.node.service.xray.config.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.xray.vo.XrayInboundRespVO;
import com.nook.biz.node.convert.xray.XrayInboundConvert;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.framework.xray.inbound.InboundParams;
import com.nook.biz.node.framework.xray.inbound.InboundParamsResolver;
import com.nook.biz.node.framework.xray.inbound.InboundProtocol;
import com.nook.biz.node.framework.xray.inbound.InboundProtocolFactory;
import com.nook.biz.node.mapper.XrayInboundMapper;
import com.nook.biz.node.service.xray.config.XrayInboundService;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Resource
    private InboundProtocolFactory inboundProtocolFactory;
    @Resource
    private XrayInstallService xrayInstallService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(XrayInboundDO entity) {
        XrayInboundDO existing = xrayInboundMapper.selectById(entity.getServerId());
        if (ObjectUtil.isNull(existing)) {
            xrayInboundMapper.insert(entity);
        } else {
            xrayInboundMapper.updateById(entity);
        }
        log.info("[xray-inbound] upsert server={} protocolKey={} port={}",
                entity.getServerId(), entity.getProtocolKey(), entity.getSharedInboundPort());
    }

    @Override
    public XrayInboundDO get(String serverId) {
        return xrayInboundMapper.selectById(serverId);
    }

    @Override
    public XrayInboundRespVO getInboundDetail(String serverId) {
        XrayInboundDO entity = xrayInboundMapper.selectById(serverId);
        if (ObjectUtil.isNull(entity)) {
            return null;
        }
        // DO → VO + 协议字段经 formPrefill 投影成 formValues; 域名绑定 (domainId/subdomain) 在 xray_install 子表, 一并取
        XrayInboundRespVO vo = XrayInboundConvert.INSTANCE.convert(entity);
        InboundParams params = InboundParamsResolver.resolve(entity.getProtocolKey(), entity.getParams());
        InboundProtocol protocol = inboundProtocolFactory.resolveByProtocol(vo.getProtocol());
        XrayInstallDO install = xrayInstallService.get(serverId);
        vo.setFormValues(protocol.formPrefill(params,
                install == null ? null : install.getDomainId(),
                install == null ? null : install.getSubdomain()));
        return vo;
    }
}
