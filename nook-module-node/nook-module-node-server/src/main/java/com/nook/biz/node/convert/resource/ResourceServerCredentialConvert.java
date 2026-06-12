package com.nook.biz.node.convert.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialRespVO;
import com.nook.biz.node.entity.ResourceServerCredentialDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ResourceServerCredentialConvert {

    ResourceServerCredentialConvert INSTANCE = Mappers.getMapper(ResourceServerCredentialConvert.class);

    ResourceServerCredentialRespVO convert(ResourceServerCredentialDO bean);

    ResourceServerCredentialRespDTO toRespDTO(ResourceServerCredentialDO bean);
}
