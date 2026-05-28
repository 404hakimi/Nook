package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ResourceServerLandingApi} 实现 (组合主表 lifecycle + landing 子表 status/ipType).
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerLandingApiImpl implements ResourceServerLandingApi {

    private final ResourceServerMapper serverMapper;
    private final ResourceServerLandingMapper landingMapper;

    @Override
    public List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, ResourceServerDO> srvMap = serverMapper.selectBatchIds(serverIds).stream()
                .collect(Collectors.toMap(ResourceServerDO::getId, Function.identity()));
        Map<String, ResourceServerLandingDO> landingMap = landingMapper.selectBatchIds(serverIds).stream()
                .collect(Collectors.toMap(ResourceServerLandingDO::getServerId, Function.identity()));
        return serverIds.stream()
                .map(id -> {
                    ResourceServerDO s = srvMap.get(id);
                    if (s == null) {
                        return null;
                    }
                    LandingSummaryDTO dto = new LandingSummaryDTO();
                    dto.setServerId(id);
                    dto.setLifecycleState(s.getLifecycleState());
                    dto.setIpAddress(s.getIpAddress());
                    ResourceServerLandingDO l = landingMap.get(id);
                    if (l != null) {
                        dto.setStatus(l.getStatus());
                        dto.setIpTypeId(l.getIpTypeId());
                    }
                    return dto;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
