package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.api.enums.XraySecurityEnum;
import com.nook.biz.node.api.xray.dto.XrayInboundDTO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.framework.xray.inbound.config.InboundParams;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface XrayInboundApiConvert {

    XrayInboundApiConvert INSTANCE = Mappers.getMapper(XrayInboundApiConvert.class);

    @Mapping(target = "host", source = "host")
    @Mapping(target = "port", source = "cfg.sharedInboundPort")
    @Mapping(target = "protocol", ignore = true)
    @Mapping(target = "transport", ignore = true)
    @Mapping(target = "security", ignore = true)
    @Mapping(target = "tls", ignore = true)
    @Mapping(target = "wsPath", ignore = true)
    @Mapping(target = "flow", ignore = true)
    @Mapping(target = "publicKey", ignore = true)
    @Mapping(target = "shortId", ignore = true)
    @Mapping(target = "serverName", ignore = true)
    @Mapping(target = "fingerprint", ignore = true)
    XrayInboundDTO toBaseInboundDTO(XrayInboundDO cfg, String host);

    /** 组装完整连接 DTO: 协议/传输/安全层由 protocol_key 解出, ws/reality 客户端参数从 params 取. */
    default XrayInboundDTO toInboundDTO(XrayInboundDO cfg, String host, InboundParams params) {
        XrayInboundDTO dto = this.toBaseInboundDTO(cfg, host);
        // 协议/传输/安全层由 protocol_key 解出 (替代旧 protocol/transport/security 列)
        XrayInboundProtocolEnum proto = XrayInboundProtocolEnum.fromKey(cfg.getProtocolKey());
        if (proto != null) {
            dto.setProtocol(proto.getProtocol());
            dto.setTransport(proto.getTransport());
            dto.setSecurity(proto.getSecurity());
            dto.setTls(XraySecurityEnum.TLS.matches(proto.getSecurity()));
        }
        if (params == null) {
            return dto;
        }
        // ws path 从 params 取 (替代旧 ws_path 列)
        if (params.getWs() != null) {
            dto.setWsPath(params.getWs().getPath());
        }
        dto.setFlow(params.getFlow());
        // reality 客户端连接参数 (pbk/sni/sid/fp)
        InboundParams.RealityParams reality = params.getReality();
        if (reality != null) {
            dto.setPublicKey(reality.getPublicKey());
            dto.setServerName(CollUtil.getFirst(reality.getServerNames()));
            dto.setShortId(this.firstShortId(reality.getShortIds()));
            dto.setFingerprint(reality.getFingerprint());
        }
        return dto;
    }

    default String firstShortId(List<String> shortIds) {
        if (CollUtil.isEmpty(shortIds)) {
            return null;
        }
        for (String s : shortIds) {
            if (StrUtil.isNotBlank(s)) {
                return s;
            }
        }
        return shortIds.get(0);
    }
}
