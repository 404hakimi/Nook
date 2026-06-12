package com.nook.biz.node.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 资源服务器额度 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerQuotaMapper extends BaseMapper<ResourceServerQuotaDO> {

    default int updateQuota(String serverId, Integer totalGb, Integer usablePercent, Integer bandwidthMbps,
                            String resetPolicy, Integer resetDay) {
        return update(null, Wrappers.<ResourceServerQuotaDO>lambdaUpdate()
                .set(totalGb != null, ResourceServerQuotaDO::getTotalGb, totalGb)
                .set(usablePercent != null, ResourceServerQuotaDO::getUsablePercent, usablePercent)
                .set(bandwidthMbps != null, ResourceServerQuotaDO::getBandwidthMbps, bandwidthMbps)
                .set(resetPolicy != null, ResourceServerQuotaDO::getResetPolicy, resetPolicy)
                .set(resetDay != null, ResourceServerQuotaDO::getResetDay, resetDay)
                .set(ResourceServerQuotaDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerQuotaDO::getServerId, serverId));
    }

}
