package com.nook.biz.node.convert.xray;

import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.controller.xray.vo.XrayInboundRespVO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.framework.xray.inbound.InboundParams;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface XrayInboundConvert {

    XrayInboundConvert INSTANCE = Mappers.getMapper(XrayInboundConvert.class);

    @Mapping(target = "protocol", ignore = true)
    @Mapping(target = "transport", ignore = true)
    @Mapping(target = "wsPath", ignore = true)
    @Mapping(target = "domain", ignore = true)
    @Mapping(target = "tlsCertPath", ignore = true)
    @Mapping(target = "tlsKeyPath", ignore = true)
    XrayInboundRespVO toBase(XrayInboundDO entity);

    /** 协议/传输由 protocol_key 解出, ws/域名/tls 路径由协议 params 多态投影 (vless-reality 无这些字段, 留空). */
    default XrayInboundRespVO convert(XrayInboundDO entity, InboundParams params) {
        XrayInboundRespVO vo = this.toBase(entity);
        XrayInboundProtocolEnum proto = XrayInboundProtocolEnum.fromKey(entity.getProtocolKey());
        if (proto != null) {
            vo.setProtocol(proto.getProtocol());
            vo.setTransport(proto.getTransport());
        }
        if (params != null) {
            vo.setWsPath(params.getWsPath());
            vo.setDomain(params.getDomain());
            vo.setTlsCertPath(params.getCertPath());
            vo.setTlsKeyPath(params.getKeyPath());
        }
        return vo;
    }
}
