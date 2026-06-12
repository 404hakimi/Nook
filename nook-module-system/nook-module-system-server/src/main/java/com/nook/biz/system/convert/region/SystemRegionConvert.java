package com.nook.biz.system.convert.region;

import com.nook.biz.system.controller.region.vo.SystemRegionRespVO;
import com.nook.biz.system.entity.SystemRegionDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface SystemRegionConvert {

    SystemRegionConvert INSTANCE = Mappers.getMapper(SystemRegionConvert.class);

    SystemRegionRespVO convert(SystemRegionDO entity);

    List<SystemRegionRespVO> convertList(List<SystemRegionDO> list);
}
