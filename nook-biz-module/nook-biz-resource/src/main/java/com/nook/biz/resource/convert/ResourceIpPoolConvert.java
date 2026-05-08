package com.nook.biz.resource.convert;

import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolRespVO;
import com.nook.biz.resource.controller.ip.vo.ResourceIpTypeRespVO;
import com.nook.biz.resource.entity.ResourceIpPool;
import com.nook.biz.resource.entity.ResourceIpType;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** ResourceIpPool / ResourceIpType 实体 ↔ VO 转换。 */
@Mapper
public interface ResourceIpPoolConvert {

    ResourceIpPoolConvert INSTANCE = Mappers.getMapper(ResourceIpPoolConvert.class);

    /** 实体 → 列表/详情 RespVO; 含 socks5Password 明文 (DB 明文存储, 与运营受信场景对齐)。 */
    ResourceIpPoolRespVO convert(ResourceIpPool entity);

    List<ResourceIpPoolRespVO> convertList(List<ResourceIpPool> entities);

    default PageResult<ResourceIpPoolRespVO> convertPage(PageResult<ResourceIpPool> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    /** IP 类型 → RespVO. */
    ResourceIpTypeRespVO convertType(ResourceIpType entity);

    List<ResourceIpTypeRespVO> convertTypeList(List<ResourceIpType> entities);
}
