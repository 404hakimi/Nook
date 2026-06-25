package com.nook.biz.node.convert.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.controller.resource.vo.ResourceServerRespVO;
import com.nook.biz.node.entity.ResourceServerDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ResourceServerConvert {

    ResourceServerConvert INSTANCE = Mappers.getMapper(ResourceServerConvert.class);

    ResourceServerRespVO convert(ResourceServerDO bean);

    ResourceServerRespDTO toRespDTO(ResourceServerDO bean);

    List<ResourceServerRespDTO> toRespDTOList(List<ResourceServerDO> list);
}
