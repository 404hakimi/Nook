package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerBillingRespVO;
import com.nook.biz.node.entity.ResourceServerBillingDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ResourceServerBillingConvert {

    ResourceServerBillingConvert INSTANCE = Mappers.getMapper(ResourceServerBillingConvert.class);

    ResourceServerBillingRespVO convert(ResourceServerBillingDO bean);
}
