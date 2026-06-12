package com.nook.biz.node.convert.xray;

import com.nook.biz.node.controller.xray.vo.XrayInboundRespVO;
import com.nook.biz.node.entity.XrayInboundDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface XrayInboundConvert {

    XrayInboundConvert INSTANCE = Mappers.getMapper(XrayInboundConvert.class);

    XrayInboundRespVO convert(XrayInboundDO entity);
}
