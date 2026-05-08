package com.nook.biz.resource.convert;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolRespVO;
import com.nook.biz.resource.controller.ip.vo.ResourceIpTypeRespVO;
import com.nook.biz.resource.entity.ResourceIpPool;
import com.nook.biz.resource.entity.ResourceIpType;
import com.nook.common.web.response.PageResult;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** ResourceIpPool / ResourceIpType 实体 ↔ VO 转换。 */
@Mapper
public interface ResourceIpPoolConvert {

    ResourceIpPoolConvert INSTANCE = Mappers.getMapper(ResourceIpPoolConvert.class);

    /** 实体 → 列表/详情 RespVO; SOCKS5 密码原文不下发, 仅下 socks5PasswordConfigured。 */
    @Mapping(target = "socks5PasswordConfigured", ignore = true)
    ResourceIpPoolRespVO convert(ResourceIpPool entity);

    List<ResourceIpPoolRespVO> convertList(List<ResourceIpPool> entities);

    default PageResult<ResourceIpPoolRespVO> convertPage(PageResult<ResourceIpPool> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    @AfterMapping
    default void fillCredentialFlags(ResourceIpPool src, @MappingTarget ResourceIpPoolRespVO target) {
        target.setSocks5PasswordConfigured(StrUtil.isNotBlank(src.getSocks5Password()));
    }

    /** IP 类型 → RespVO. */
    ResourceIpTypeRespVO convertType(ResourceIpType entity);

    List<ResourceIpTypeRespVO> convertTypeList(List<ResourceIpType> entities);
}
