package com.nook.biz.trade.convert;

import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface TradePlanConvert {

    TradePlanConvert INSTANCE = Mappers.getMapper(TradePlanConvert.class);

    TradePlanDO toDO(TradePlanSaveReqVO vo);

    TradePlanRespVO toRespVO(TradePlanDO bean);

    List<TradePlanRespVO> toRespVOList(List<TradePlanDO> list);
}
