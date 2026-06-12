package com.nook.biz.system.api.domain;

import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;
import com.nook.biz.system.entity.SystemDomainDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SystemDomainApiConvert {

    SystemDomainApiConvert INSTANCE = Mappers.getMapper(SystemDomainApiConvert.class);

    SystemDomainRespDTO toRespDTO(SystemDomainDO entity);
}
