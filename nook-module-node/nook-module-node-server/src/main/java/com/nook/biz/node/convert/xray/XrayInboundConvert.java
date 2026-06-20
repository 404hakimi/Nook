package com.nook.biz.node.convert.xray;

import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.controller.xray.vo.XrayInboundRespVO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.framework.xray.inbound.InboundParams;
import com.nook.biz.node.framework.xray.inbound.InboundParamsResolver;
import com.nook.biz.node.framework.xray.inbound.vmess.VmessWsParams;
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
        InboundParams params = InboundParamsResolver.resolve(entity.getProtocolKey(), entity.getParams());
        // 当前 admin 详情只展示 vmess 的 ws/tls; reality 客户端参数走订阅, 不在此 VO
        if (params instanceof VmessWsParams vmess) {
            if (vmess.getWs() != null) {
                vo.setWsPath(vmess.getWs().getPath());
            }
            if (vmess.getTls() != null) {
                vo.setDomain(vmess.getTls().getDomain());
                vo.setTlsCertPath(vmess.getTls().getCertPath());
                vo.setTlsKeyPath(vmess.getTls().getKeyPath());
            }
        }
        return vo;
    }
}
