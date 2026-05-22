package com.nook.biz.node.convert.operation;

import com.nook.biz.node.controller.operation.vo.OpConfigRespVO;
import com.nook.biz.node.controller.operation.vo.OpConfigSimpleRespVO;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Op 配置 Convert
 *
 * @author nook
 */
@Mapper
public interface OpConfigConvert {

    OpConfigConvert INSTANCE = Mappers.getMapper(OpConfigConvert.class);

    OpConfigRespVO convert(OpConfigDO entity);

    List<OpConfigRespVO> convertList(List<OpConfigDO> entities);

    OpConfigSimpleRespVO convertSimple(OpConfigDO entity);

    List<OpConfigSimpleRespVO> convertSimpleList(List<OpConfigDO> entities);
}
