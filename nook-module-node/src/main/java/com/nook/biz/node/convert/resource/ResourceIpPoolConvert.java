package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.ip.vo.ResourceIpPoolRespVO;
import com.nook.biz.node.controller.resource.ip.vo.ResourceIpTypeRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpTypeDO;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * IP 池 / IP 类型 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceIpPoolConvert {

    ResourceIpPoolConvert INSTANCE = Mappers.getMapper(ResourceIpPoolConvert.class);

    /** 实体 → 列表/详情 RespVO; 含 socks5Password 明文 (DB 明文存储, 与运营受信场景对齐)。 */
    ResourceIpPoolRespVO convert(ResourceIpPoolDO entity);

    List<ResourceIpPoolRespVO> convertList(List<ResourceIpPoolDO> entities);

    default PageResult<ResourceIpPoolRespVO> convertPage(PageResult<ResourceIpPoolDO> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    /** IP 类型 → RespVO. */
    ResourceIpTypeRespVO convertType(ResourceIpTypeDO entity);

    List<ResourceIpTypeRespVO> convertTypeList(List<ResourceIpTypeDO> entities);
}
