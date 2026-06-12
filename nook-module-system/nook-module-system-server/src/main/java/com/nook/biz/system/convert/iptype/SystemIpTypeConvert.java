package com.nook.biz.system.convert.iptype;

import com.nook.biz.system.controller.iptype.vo.SystemIpTypeRespVO;
import com.nook.biz.system.entity.SystemIpTypeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface SystemIpTypeConvert {

    SystemIpTypeConvert INSTANCE = Mappers.getMapper(SystemIpTypeConvert.class);

    SystemIpTypeRespVO convert(SystemIpTypeDO entity);

    List<SystemIpTypeRespVO> convertList(List<SystemIpTypeDO> list);
}
