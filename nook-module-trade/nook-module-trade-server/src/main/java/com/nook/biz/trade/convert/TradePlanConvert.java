package com.nook.biz.trade.convert;

import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 套餐 VO 转换; 纯转换, 不依赖 Api / Service.
 *
 * <p>容量 (capacityTotal/Available/Occupied) 不在 DO 上, 由 controller 从 service 取落地机池余量后传入.
 *
 * @author nook
 */
@Mapper
public interface TradePlanConvert {

    TradePlanConvert INSTANCE = Mappers.getMapper(TradePlanConvert.class);

    /** 套餐余量: 同区域+同IP类型+规格达标的落地机, 按 status 分桶. service 按落地机池实时算后传入. */
    record PlanCapacity(int total, int available, int occupied) { }

    TradePlanDO toDO(TradePlanSaveReqVO vo);

    /** 容量字段由带 cap 的重载补; 单独调本方法时留 null. */
    @Mapping(target = "capacityTotal", ignore = true)
    @Mapping(target = "capacityAvailable", ignore = true)
    @Mapping(target = "capacityOccupied", ignore = true)
    TradePlanRespVO toRespVO(TradePlanDO bean);

    default TradePlanRespVO toRespVO(TradePlanDO bean, PlanCapacity cap) {
        TradePlanRespVO vo = toRespVO(bean);
        if (cap != null) {
            vo.setCapacityTotal(cap.total());
            vo.setCapacityAvailable(cap.available());
            vo.setCapacityOccupied(cap.occupied());
        }
        return vo;
    }

    default PageResult<TradePlanRespVO> convertPage(PageResult<TradePlanDO> page,
                                                    Map<String, PlanCapacity> capMap) {
        List<TradePlanRespVO> records = page.getRecords().stream()
                .map(p -> toRespVO(p, capMap.get(p.getId())))
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), records);
    }
}
