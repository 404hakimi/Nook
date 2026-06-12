package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerQuotaRespDTO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import com.nook.biz.node.entity.ResourceServerTrafficDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ResourceServerQuotaApiConvert {

    ResourceServerQuotaApiConvert INSTANCE = Mappers.getMapper(ResourceServerQuotaApiConvert.class);

    @Mapping(target = "serverId", source = "quota.serverId")
    ResourceServerQuotaRespDTO toRespDTO(ResourceServerQuotaDO quota, ResourceServerTrafficDO traffic);
}
