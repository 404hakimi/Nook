package com.nook.biz.trade.convert;

import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;
import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 套餐 VO 转换; 纯转换, 不依赖 Api / Service.
 *
 * <p>容量 (capacityTotal/Available/Occupied) 不在 DO 上, 由 controller 从 node landingApi 取后传入.
 *
 * @author nook
 */
@Mapper
public interface TradePlanConvert {

    TradePlanConvert INSTANCE = Mappers.getMapper(TradePlanConvert.class);

    /** 从一批套餐抽容量匹配规格, 供 controller 一次性调 node landingApi 算容量. */
    static List<PlanSpecDTO> collectCapacitySpecs(Collection<TradePlanDO> plans) {
        return plans.stream()
                .map(p -> new PlanSpecDTO(
                        p.getId(), p.getRegionCode(), p.getIpTypeId(),
                        p.getTrafficGb() == null ? 0 : p.getTrafficGb(),
                        p.getBandwidthMbps() == null ? 0 : p.getBandwidthMbps()))
                .collect(Collectors.toList());
    }

    TradePlanDO toDO(TradePlanSaveReqVO vo);

    /** 容量字段由带 cap 的重载补; 单独调本方法时留 null. */
    @Mapping(target = "capacityTotal", ignore = true)
    @Mapping(target = "capacityAvailable", ignore = true)
    @Mapping(target = "capacityOccupied", ignore = true)
    TradePlanRespVO toRespVO(TradePlanDO bean);

    default TradePlanRespVO toRespVO(TradePlanDO bean, PlanCapacityDTO cap) {
        TradePlanRespVO vo = toRespVO(bean);
        if (cap != null) {
            vo.setCapacityTotal(cap.getTotal());
            vo.setCapacityAvailable(cap.getAvailable());
            vo.setCapacityOccupied(cap.getOccupied());
        }
        return vo;
    }

    default PageResult<TradePlanRespVO> convertPage(PageResult<TradePlanDO> page,
                                                    Map<String, PlanCapacityDTO> capMap) {
        List<TradePlanRespVO> records = page.getRecords().stream()
                .map(p -> toRespVO(p, capMap.get(p.getId())))
                .collect(Collectors.toList());
        return PageResult.of(page.getTotal(), records);
    }
}
