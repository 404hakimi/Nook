package com.nook.biz.node.convert.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 服务器运行时 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceServerRuntimeConvert {

    ResourceServerRuntimeConvert INSTANCE = Mappers.getMapper(ResourceServerRuntimeConvert.class);

    ResourceServerRuntimeRespDTO toRespDTO(ResourceServerRuntimeDO bean);
}
