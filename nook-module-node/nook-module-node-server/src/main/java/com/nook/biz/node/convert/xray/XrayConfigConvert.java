package com.nook.biz.node.convert.xray;

import com.nook.biz.node.controller.xray.vo.XrayConfigRespVO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Xray inbound 共享配置 Convert
 *
 * @author nook
 */
@Mapper
public interface XrayConfigConvert {

    XrayConfigConvert INSTANCE = Mappers.getMapper(XrayConfigConvert.class);

    XrayConfigRespVO convert(XrayConfigDO entity);
}
