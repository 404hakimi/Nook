package com.nook.biz.node.convert.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.controller.resource.vo.ResourceServerFrontlineRespVO;
import com.nook.biz.node.controller.resource.vo.ServerFrontlineListItemRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerQuotaDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;
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

    /** 单条详情拼装: 服务器主表 + 关联表回填 (单条, 不分页). */
    default ServerFrontlineListItemRespVO convertSingleWithRuntime(ResourceServerDO server,
                                                                   ResourceServerCredentialDO cred,
                                                                   ResourceServerRuntimeDO runtime,
                                                                   ResourceServerQuotaDO quota,
                                                                   ResourceServerTrafficDO traffic,
                                                                   XrayServerDO xray,
                                                                   LocalDateTime now) {
        return toListItem(server, cred, runtime, quota, traffic, xray, now);
    }

    /** 列表批量拼装: 服务器主表 + 关联表回填 → 列表项 VO 分页. */
    default PageResult<ServerFrontlineListItemRespVO> convertPageWithRuntime(
            PageResult<ResourceServerDO> page,
            Map<String, ResourceServerCredentialDO> credMap,
            Map<String, ResourceServerRuntimeDO> runtimeMap,
            Map<String, ResourceServerQuotaDO> quotaMap,
            Map<String, ResourceServerTrafficDO> trafficMap,
            Map<String, XrayServerDO> xrayMap,
            LocalDateTime now) {
        List<ResourceServerDO> records = page.getRecords();
        List<ServerFrontlineListItemRespVO> list = new ArrayList<>(records.size());
        for (ResourceServerDO s : records) {
            list.add(toListItem(s,
                    ObjectUtil.isNull(credMap) ? null : credMap.get(s.getId()),
                    ObjectUtil.isNull(runtimeMap) ? null : runtimeMap.get(s.getId()),
                    ObjectUtil.isNull(quotaMap) ? null : quotaMap.get(s.getId()),
                    ObjectUtil.isNull(trafficMap) ? null : trafficMap.get(s.getId()),
                    ObjectUtil.isNull(xrayMap) ? null : xrayMap.get(s.getId()),
                    now));
        }
        return PageResult.of(page.getTotal(), list);
    }

    /** 组装单行: server 主表 + runtime + 配额配置 + 当周期测量 + xray. */
    static ServerFrontlineListItemRespVO toListItem(ResourceServerDO s,
                                                    ResourceServerCredentialDO credential,
                                                    ResourceServerRuntimeDO rt,
                                                    ResourceServerQuotaDO quota,
                                                    ResourceServerTrafficDO traffic,
                                                    XrayServerDO xray,
                                                    LocalDateTime now) {
        ServerFrontlineListItemRespVO vo = new ServerFrontlineListItemRespVO();
        vo.setId(s.getId());
        vo.setName(s.getName());
        // host 取服务器主表 IP, 凭据不再持有 host
        vo.setHost(s.getIpAddress());
        vo.setRegion(s.getRegion());
        vo.setLifecycleState(s.getLifecycleState());
        Long elapsedSec = null;
        if (ObjectUtil.isNotNull(rt)) {
            vo.setAgentVersion(rt.getAgentVersion());
            vo.setLastHeartbeatAt(rt.getLastHeartbeatAt());
            if (ObjectUtil.isNotNull(rt.getLastHeartbeatAt())) {
                elapsedSec = Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
                vo.setElapsedSec(elapsedSec);
            }
        }
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec).name());
        if (ObjectUtil.isNotNull(quota)) {
            vo.setTotalGb(quota.getTotalGb());
        }
        if (ObjectUtil.isNotNull(traffic)) {
            vo.setRxBytes(traffic.getRxBytes());
            vo.setTxBytes(traffic.getTxBytes());
            vo.setUsedBytes(traffic.getUsedBytes());
            vo.setThrottleState(traffic.getThrottleState());
        }
        if (ObjectUtil.isNotNull(xray)) {
            vo.setXrayVersion(xray.getXrayVersion());
        }
        return vo;
    }
}
