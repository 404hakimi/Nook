package com.nook.biz.system.convert;

import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;
import com.nook.biz.system.controller.domain.vo.SystemDomainRespVO;
import com.nook.biz.system.dal.dataobject.domain.SystemDomainDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 系统域名 Convert
 *
 * @author nook
 */
@Mapper
public interface SystemDomainConvert {

    SystemDomainConvert INSTANCE = Mappers.getMapper(SystemDomainConvert.class);

    SystemDomainRespVO convert(SystemDomainDO bean);

    List<SystemDomainRespVO> convertList(List<SystemDomainDO> list);

    SystemDomainRespDTO toRespDTO(SystemDomainDO bean);
}
