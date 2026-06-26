package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.biz.node.entity.ResourceServerRuntimeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ResourceServerRuntimeApiConvert {

    ResourceServerRuntimeApiConvert INSTANCE = Mappers.getMapper(ResourceServerRuntimeApiConvert.class);

    ResourceServerRuntimeRespDTO toRespDTO(ResourceServerRuntimeDO bean);
}
