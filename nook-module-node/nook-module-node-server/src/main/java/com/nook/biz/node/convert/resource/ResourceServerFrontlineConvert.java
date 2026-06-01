package com.nook.biz.node.convert.resource;

import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.controller.resource.vo.ResourceServerFrontlineRespVO;
import com.nook.biz.node.controller.resource.vo.ServerFrontlineListItemRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 线路机扩展 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceServerFrontlineConvert {

    ResourceServerFrontlineConvert INSTANCE = Mappers.getMapper(ResourceServerFrontlineConvert.class);

    ResourceServerFrontlineRespVO convert(ResourceServerFrontlineDO bean);

    /** 提取 serverId 集合, 供 Controller 批量查关联 (规范三步走 ①). */
    default Set<String> extractServerIds(List<ResourceServerDO> records) {
        return CollectionUtils.convertSet(records, ResourceServerDO::getId);
    }

    /** 单条 detail 拼装: server 主表 + 5 个 enrich (单条收敛, 不分页). */
    default ServerFrontlineListItemRespVO convertSingleWithRuntime(ResourceServerDO server,
                                                                   ResourceServerCredentialDO cred,
                                                                   ResourceServerRuntimeDO runtime,
                                                                   ResourceServerCapacityDO capacity,
                                                                   XrayServerDO xray,
                                                                   LocalDateTime now) {
        return toListItem(server, cred, runtime, capacity, xray, now);
    }

    /** 列表批量拼装: server 主表 + 5 个 enrich map → 列表项 VO 分页. */
    default PageResult<ServerFrontlineListItemRespVO> convertPageWithRuntime(
            PageResult<ResourceServerDO> page,
            Map<String, ResourceServerCredentialDO> credMap,
            Map<String, ResourceServerRuntimeDO> runtimeMap,
            Map<String, ResourceServerCapacityDO> capacityMap,
            Map<String, XrayServerDO> xrayMap,
            LocalDateTime now) {
        List<ResourceServerDO> records = page.getRecords();
        List<ServerFrontlineListItemRespVO> list = new ArrayList<>(records.size());
        for (ResourceServerDO s : records) {
            list.add(toListItem(s,
                    credMap == null ? null : credMap.get(s.getId()),
                    runtimeMap == null ? null : runtimeMap.get(s.getId()),
                    capacityMap == null ? null : capacityMap.get(s.getId()),
                    xrayMap == null ? null : xrayMap.get(s.getId()),
                    now));
        }
        return PageResult.of(page.getTotal(), list);
    }

    /** 组装单行: server 主表 + runtime + capacity + xray. */
    static ServerFrontlineListItemRespVO toListItem(ResourceServerDO s,
                                                    ResourceServerCredentialDO credential,
                                                    ResourceServerRuntimeDO rt,
                                                    ResourceServerCapacityDO cap,
                                                    XrayServerDO xray,
                                                    LocalDateTime now) {
        ServerFrontlineListItemRespVO vo = new ServerFrontlineListItemRespVO();
        vo.setId(s.getId());
        vo.setName(s.getName());
        // host = server.ipAddress (canonical); credential 不再持有 host
        vo.setHost(s.getIpAddress());
        vo.setRegion(s.getRegion());
        vo.setLifecycleState(s.getLifecycleState());
        Long elapsedSec = null;
        if (rt != null) {
            vo.setAgentVersion(rt.getAgentVersion());
            vo.setLastHeartbeatAt(rt.getLastHeartbeatAt());
            if (rt.getLastHeartbeatAt() != null) {
                elapsedSec = Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
                vo.setElapsedSec(elapsedSec);
            }
        }
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec).name());
        if (cap != null) {
            vo.setMonthlyTrafficGb(cap.getMonthlyTrafficGb());
            vo.setRxBytes(cap.getRxBytes());
            vo.setTxBytes(cap.getTxBytes());
            vo.setUsedTrafficBytes(cap.getUsedTrafficBytes());
            vo.setThrottleState(cap.getThrottleState());
        }
        if (xray != null) {
            vo.setXrayVersion(xray.getXrayVersion());
        }
        return vo;
    }
}
