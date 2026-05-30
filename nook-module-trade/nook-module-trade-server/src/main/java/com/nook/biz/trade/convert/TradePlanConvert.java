package com.nook.biz.trade.convert;

import com.nook.biz.node.api.resource.dto.PlanCapacityDTO;
import com.nook.biz.node.api.resource.dto.PlanSpecDTO;
import com.nook.biz.trade.controller.vo.TradePlanRespVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
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

    TradePlanDO toDO(TradePlanSaveReqVO vo);

    @Mapping(target = "capacityTotal", source = "cap.total")
    @Mapping(target = "capacityAvailable", source = "cap.available")
    @Mapping(target = "capacityOccupied", source = "cap.occupied")
    TradePlanRespVO toRespVO(TradePlanDO plan, PlanCapacityDTO cap);

    default List<PlanSpecDTO> toSpecs(Collection<TradePlanDO> plans) {
        // 流量 / 带宽为 null 视作 0 (不限), 避免拆箱 NPE
        return CollectionUtils.convertList(plans, p -> new PlanSpecDTO(
                p.getId(), p.getRegionCode(), p.getIpTypeId(),
                p.getTrafficGb() == null ? 0 : p.getTrafficGb(),
                p.getBandwidthMbps() == null ? 0 : p.getBandwidthMbps()));
    }

    default PageResult<TradePlanRespVO> convertPage(PageResult<TradePlanDO> page,
                                                    Map<String, PlanCapacityDTO> capMap) {
        return PageResult.of(page.getTotal(),
                CollectionUtils.convertList(page.getRecords(), p -> toRespVO(p, capMap.get(p.getId()))));
    }
}
