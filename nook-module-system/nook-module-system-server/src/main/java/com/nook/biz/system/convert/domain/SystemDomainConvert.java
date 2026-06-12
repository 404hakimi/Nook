package com.nook.biz.system.convert.domain;

import com.nook.biz.system.controller.domain.vo.SystemDomainRespVO;
import com.nook.biz.system.entity.SystemDomainDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface SystemDomainConvert {

    SystemDomainConvert INSTANCE = Mappers.getMapper(SystemDomainConvert.class);

    SystemDomainRespVO convert(SystemDomainDO entity);

    List<SystemDomainRespVO> convertList(List<SystemDomainDO> list);
}
