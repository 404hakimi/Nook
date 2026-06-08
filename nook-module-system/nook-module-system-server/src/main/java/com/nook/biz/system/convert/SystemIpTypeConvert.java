package com.nook.biz.system.convert;

import com.nook.biz.system.api.iptype.dto.SystemIpTypeRespDTO;
import com.nook.biz.system.dal.dataobject.iptype.SystemIpTypeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * IP 类型 Convert
 *
 * @author nook
 */
@Mapper
public interface SystemIpTypeConvert {

    SystemIpTypeConvert INSTANCE = Mappers.getMapper(SystemIpTypeConvert.class);

    SystemIpTypeRespDTO toRespDTO(SystemIpTypeDO bean);
}
