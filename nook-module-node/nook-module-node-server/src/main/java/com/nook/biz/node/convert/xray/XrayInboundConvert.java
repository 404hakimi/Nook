package com.nook.biz.node.convert.xray;

import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.controller.xray.vo.XrayInboundRespVO;
import com.nook.biz.node.entity.XrayInboundDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface XrayInboundConvert {

    XrayInboundConvert INSTANCE = Mappers.getMapper(XrayInboundConvert.class);

    @Mapping(target = "protocol", ignore = true)
    @Mapping(target = "formValues", ignore = true)
    XrayInboundRespVO toBase(XrayInboundDO entity);

    /** 协议判别键由 protocol_key 解出; 协议字段值 (formValues) 由 controller 用 formPrefill 填 (需 install 的域名绑定 + params). */
    default XrayInboundRespVO convert(XrayInboundDO entity) {
        XrayInboundRespVO vo = this.toBase(entity);
        XrayInboundProtocolEnum proto = XrayInboundProtocolEnum.fromKey(entity.getProtocolKey());
        if (proto != null) {
            vo.setProtocol(proto.getProtocol());
        }
        return vo;
    }
}
