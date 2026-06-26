package com.nook.biz.node.api.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ResourceServerLandingApiConvert {

    ResourceServerLandingApiConvert INSTANCE = Mappers.getMapper(ResourceServerLandingApiConvert.class);

    // 主表 + landing 子表 → 跨模块概要 DTO; serverId 取主表 id, landing 为 null 时 status/ipType 留空
    @Mapping(target = "serverId", source = "server.id")
    LandingSummaryDTO toSummary(ResourceServerDO server, Socks5InstallDO landing);

    default List<LandingSummaryDTO> toSummaries(Collection<String> serverIds,
                                                Map<String, ResourceServerDO> serverMap,
                                                Map<String, Socks5InstallDO> landingMap) {
        List<LandingSummaryDTO> list = new ArrayList<>(serverIds.size());
        for (String serverId : serverIds) {
            ResourceServerDO server = serverMap.get(serverId);
            if (ObjectUtil.isNotNull(server)) {
                list.add(toSummary(server, landingMap.get(serverId)));
            }
        }
        return list;
    }
}
