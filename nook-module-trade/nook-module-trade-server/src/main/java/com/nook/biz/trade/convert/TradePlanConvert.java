package com.nook.biz.trade.convert;

import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;
import com.nook.biz.trade.controller.vo.TradePlanCreateReqVO;
import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface TradePlanConvert {

    TradePlanConvert INSTANCE = Mappers.getMapper(TradePlanConvert.class);

    TradePlanDO toDO(TradePlanCreateReqVO vo);

    @Mapping(target = "capacityTotal", source = "cap.total")
    @Mapping(target = "capacityAvailable", source = "cap.available")
    @Mapping(target = "capacityOccupied", source = "cap.occupied")
    @Mapping(target = "activeSubCount", ignore = true) // 由 convertPage 按行补
    TradePlanRespVO toRespVO(TradePlanDO plan, PlanCapacityDTO cap);

    default List<PlanSpecDTO> toSpecs(Collection<TradePlanDO> plans) {
        // 流量 / 带宽为 null 视作 0 (不限), 避免拆箱 NPE
        return CollectionUtils.convertList(plans, p -> new PlanSpecDTO(
                p.getId(), p.getRegionCode(), p.getIpTypeId(),
                p.getTrafficGb() == null ? 0 : p.getTrafficGb(),
                p.getBandwidthMbps() == null ? 0 : p.getBandwidthMbps()));
    }

    default PageResult<TradePlanRespVO> convertPage(PageResult<TradePlanDO> page,
                                                    Map<String, PlanCapacityDTO> capMap,
                                                    Map<String, Integer> subCountMap) {
        return PageResult.of(page.getTotal(), CollectionUtils.convertList(page.getRecords(), p -> {
            TradePlanRespVO vo = toRespVO(p, capMap.get(p.getId()));
            vo.setActiveSubCount(subCountMap.getOrDefault(p.getId(), 0));
            return vo;
        }));
    }
}
