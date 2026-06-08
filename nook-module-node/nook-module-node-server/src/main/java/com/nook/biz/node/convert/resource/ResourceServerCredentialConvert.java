package com.nook.biz.node.convert.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 服务器 SSH 凭据 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceServerCredentialConvert {

    ResourceServerCredentialConvert INSTANCE = Mappers.getMapper(ResourceServerCredentialConvert.class);

    ResourceServerCredentialRespVO convert(ResourceServerCredentialDO bean);

    ResourceServerCredentialRespDTO toRespDTO(ResourceServerCredentialDO bean);
}
