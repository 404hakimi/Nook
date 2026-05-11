package com.nook.biz.node.resource.convert;

import com.nook.biz.node.resource.controller.server.vo.ResourceServerRespVO;
import com.nook.biz.node.resource.entity.ResourceServer;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** ResourceServer 实体 ↔ VO. */
@Mapper
public interface ResourceServerConvert {

    ResourceServerConvert INSTANCE = Mappers.getMapper(ResourceServerConvert.class);

    /** 实体 → 列表/详情 RespVO; 含 sshPassword 明文 (DB 明文存, 与运营受信场景对齐). */
    ResourceServerRespVO convert(ResourceServer entity);

    List<ResourceServerRespVO> convertList(List<ResourceServer> entities);

    default PageResult<ResourceServerRespVO> convertPage(PageResult<ResourceServer> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }
}
