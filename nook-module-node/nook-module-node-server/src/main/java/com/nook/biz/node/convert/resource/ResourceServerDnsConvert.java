package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerDnsRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDnsDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 服务器 DNS 绑定 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceServerDnsConvert {

    ResourceServerDnsConvert INSTANCE = Mappers.getMapper(ResourceServerDnsConvert.class);

    ResourceServerDnsRespVO convert(ResourceServerDnsDO bean);
}
