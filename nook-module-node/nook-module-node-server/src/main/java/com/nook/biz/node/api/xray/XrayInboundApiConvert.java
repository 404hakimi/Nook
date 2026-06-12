package com.nook.biz.node.api.xray;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.xray.dto.XrayInboundDTO;
import com.nook.biz.node.entity.XrayInboundDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(imports = StrUtil.class)
public interface XrayInboundApiConvert {

    XrayInboundApiConvert INSTANCE = Mappers.getMapper(XrayInboundApiConvert.class);

    @Mapping(target = "host", source = "host")
    @Mapping(target = "port", source = "cfg.sharedInboundPort")
    // TLS 开关按证书路径是否配置推导
    @Mapping(target = "tls", expression = "java(StrUtil.isNotBlank(cfg.getTlsCertPath()))")
    XrayInboundDTO toInboundDTO(XrayInboundDO cfg, String host);
}
