package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.entity.ResourceServerDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ResourceServerApiConvert {

    ResourceServerApiConvert INSTANCE = Mappers.getMapper(ResourceServerApiConvert.class);

    ResourceServerRespDTO toRespDTO(ResourceServerDO bean);

    List<ResourceServerRespDTO> toRespDTOList(List<ResourceServerDO> list);
}
