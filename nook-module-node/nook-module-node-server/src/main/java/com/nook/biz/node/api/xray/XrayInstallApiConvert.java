package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayInstallRespDTO;
import com.nook.biz.node.entity.XrayInstallDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface XrayInstallApiConvert {

    XrayInstallApiConvert INSTANCE = Mappers.getMapper(XrayInstallApiConvert.class);

    XrayInstallRespDTO toRespDTO(XrayInstallDO entity);
}
