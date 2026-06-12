package com.nook.biz.node.convert.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.controller.resource.vo.ResourceServerRespVO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ResourceServerConvert {

    ResourceServerConvert INSTANCE = Mappers.getMapper(ResourceServerConvert.class);

    ResourceServerRespVO convert(ResourceServerDO bean);

    List<ResourceServerRespVO> convertList(List<ResourceServerDO> list);

    default PageResult<ResourceServerRespVO> convertPage(PageResult<ResourceServerDO> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    ResourceServerRespDTO toRespDTO(ResourceServerDO bean);

    List<ResourceServerRespDTO> toRespDTOList(List<ResourceServerDO> list);
}
