package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.entity.ResourceServerCredentialDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ResourceServerCredentialApiConvert {

    ResourceServerCredentialApiConvert INSTANCE = Mappers.getMapper(ResourceServerCredentialApiConvert.class);

    ResourceServerCredentialRespDTO toRespDTO(ResourceServerCredentialDO bean);
}
