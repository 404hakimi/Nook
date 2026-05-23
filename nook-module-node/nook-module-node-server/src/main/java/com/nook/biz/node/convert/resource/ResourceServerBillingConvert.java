package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerBillingRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 服务器账面 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceServerBillingConvert {

    ResourceServerBillingConvert INSTANCE = Mappers.getMapper(ResourceServerBillingConvert.class);

    ResourceServerBillingRespVO convert(ResourceServerBillingDO bean);
}
