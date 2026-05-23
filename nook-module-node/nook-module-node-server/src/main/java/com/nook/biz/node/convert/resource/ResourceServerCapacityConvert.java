package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerCapacityRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 服务器容量 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceServerCapacityConvert {

    ResourceServerCapacityConvert INSTANCE = Mappers.getMapper(ResourceServerCapacityConvert.class);

    ResourceServerCapacityRespVO convert(ResourceServerCapacityDO bean);
}
