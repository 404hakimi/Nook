package com.nook.biz.node.convert.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.resource.vo.ResourceServerQuotaRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerQuotaDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 服务器配额 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceServerQuotaConvert {

    ResourceServerQuotaConvert INSTANCE = Mappers.getMapper(ResourceServerQuotaConvert.class);

    /** 配额配置 + 当周期测量 → RespVO. */
    default ResourceServerQuotaRespVO convert(ResourceServerQuotaDO quota, ResourceServerTrafficDO traffic) {
        ResourceServerQuotaRespVO vo = new ResourceServerQuotaRespVO();
        if (ObjectUtil.isNull(quota)) {
            return vo;
        }
        vo.setServerId(quota.getServerId());
        vo.setTotalGb(quota.getTotalGb());
        vo.setUsablePercent(quota.getUsablePercent());
        vo.setBandwidthMbps(quota.getBandwidthMbps());
        vo.setResetPolicy(quota.getResetPolicy());
        vo.setResetDay(quota.getResetDay());
        if (ObjectUtil.isNotNull(traffic)) {
            vo.setRxBytes(traffic.getRxBytes());
            vo.setTxBytes(traffic.getTxBytes());
            vo.setUsedBytes(traffic.getUsedBytes());
            vo.setThrottleState(traffic.getThrottleState());
        }
        return vo;
    }
}
