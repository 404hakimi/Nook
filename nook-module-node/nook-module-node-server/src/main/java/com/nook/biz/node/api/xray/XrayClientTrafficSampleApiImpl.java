package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.XrayClientTrafficSampleApi;
import com.nook.biz.node.api.xray.dto.AgentStatSnapshotDTO;
import com.nook.biz.node.api.xray.dto.SampleStatDTO;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService.AgentStatSnapshot;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService.SampleStat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** node-api {@link XrayClientTrafficSampleApi} 实现; DTO ↔ 内部 record 互转后委托给 service. */
@Service
@RequiredArgsConstructor
public class XrayClientTrafficSampleApiImpl implements XrayClientTrafficSampleApi {

    private final XrayClientTrafficSampleService xrayClientTrafficSampleService;

    @Override
    public SampleStatDTO applyAgentStats(String serverId, Map<String, AgentStatSnapshotDTO> snapshot) {
        Map<String, AgentStatSnapshot> internal = new HashMap<>(snapshot == null ? 0 : snapshot.size());
        if (snapshot != null) {
            snapshot.forEach((email, dto) ->
                    internal.put(email, new AgentStatSnapshot(dto.upBytes(), dto.downBytes())));
        }
        SampleStat stat = xrayClientTrafficSampleService.applyAgentStats(serverId, internal);
        return new SampleStatDTO(stat.upserted(), stat.skipped());
    }
}
