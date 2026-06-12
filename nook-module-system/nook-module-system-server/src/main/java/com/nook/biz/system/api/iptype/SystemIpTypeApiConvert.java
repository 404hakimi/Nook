package com.nook.biz.system.api.iptype;

import com.nook.biz.system.api.iptype.dto.SystemIpTypeRespDTO;
import com.nook.biz.system.entity.SystemIpTypeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SystemIpTypeApiConvert {

    SystemIpTypeApiConvert INSTANCE = Mappers.getMapper(SystemIpTypeApiConvert.class);

    SystemIpTypeRespDTO toRespDTO(SystemIpTypeDO entity);
}
