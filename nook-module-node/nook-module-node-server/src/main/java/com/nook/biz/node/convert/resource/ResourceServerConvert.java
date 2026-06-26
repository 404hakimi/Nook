package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerRespVO;
import com.nook.biz.node.entity.ResourceServerDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ResourceServerConvert {

    ResourceServerConvert INSTANCE = Mappers.getMapper(ResourceServerConvert.class);

    ResourceServerRespVO convert(ResourceServerDO bean);
}
