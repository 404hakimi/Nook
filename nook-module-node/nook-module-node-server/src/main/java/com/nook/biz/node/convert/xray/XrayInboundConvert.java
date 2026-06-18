package com.nook.biz.node.convert.xray;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.controller.xray.vo.XrayInboundRespVO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.framework.xray.inbound.config.InboundParams;
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

    /** 协议/传输由 protocol_key 解出, ws/域名/tls 路径从 params 取 (旧列已删). */
    default XrayInboundRespVO convert(XrayInboundDO entity) {
        XrayInboundRespVO vo = this.toBase(entity);
        XrayInboundProtocolEnum proto = XrayInboundProtocolEnum.fromKey(entity.getProtocolKey());
        if (proto != null) {
            vo.setProtocol(proto.getProtocol());
            vo.setTransport(proto.getTransport());
        }
        InboundParams params = StrUtil.isBlank(entity.getParams())
                ? null : JSON.parseObject(entity.getParams(), InboundParams.class);
        if (params == null) {
            return vo;
        }
        if (params.getWs() != null) {
            vo.setWsPath(params.getWs().getPath());
        }
        if (params.getTls() != null) {
            vo.setDomain(params.getTls().getDomain());
            vo.setTlsCertPath(params.getTls().getCertPath());
            vo.setTlsKeyPath(params.getTls().getKeyPath());
        }
        return vo;
    }
}
