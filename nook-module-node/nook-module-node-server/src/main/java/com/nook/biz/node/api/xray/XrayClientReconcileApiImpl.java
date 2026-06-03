package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.XrayClientStatusEnum;
import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayConfigMapper;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.cli.XrayInboundCli;
import com.nook.biz.node.framework.xray.cli.XrayOutboundCli;
import com.nook.biz.node.framework.xray.cli.XrayRoutingCli;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link XrayClientReconcileApi} 实现; 从 DB 拼某线路机应存在的全部客户端期望态 (含预拼 adu/ado/adrules JSON).
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayClientReconcileApiImpl implements XrayClientReconcileApi {

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayConfigMapper xrayConfigMapper;
    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerLandingMapper resourceServerLandingMapper;
    @Resource
    private XrayInboundCli xrayInboundCli;
    @Resource
    private XrayOutboundCli xrayOutboundCli;
    @Resource
    private XrayRoutingCli xrayRoutingCli;

    @Override
    public List<XrayReconcileClientDTO> getDesiredClients(String serverId) {
        XrayConfigDO cfg = xrayConfigMapper.selectById(serverId);
        if (ObjectUtil.isNull(cfg)) {
            return List.of();
        }
        List<XrayClientDO> running = xrayClientMapper.selectByServerId(serverId).stream()
                .filter(c -> XrayClientStatusEnum.RUNNING.matches(c.getStatus()))
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(running)) {
            return List.of();
        }
        // 落地机主表 + 落地子表: 按 ip_id 集合各批量查一次, 避免逐 client 查 (N+1)
        Set<String> ipIds = CollectionUtils.convertSet(running, XrayClientDO::getIpId);
        Map<String, ResourceServerDO> landingSrvMap = CollUtil.isEmpty(ipIds) ? Map.of()
                : CollectionUtils.convertMap(resourceServerMapper.selectBatchIds(ipIds), ResourceServerDO::getId);
        Map<String, ResourceServerLandingDO> landingMap = CollUtil.isEmpty(ipIds) ? Map.of()
                : CollectionUtils.convertMap(resourceServerLandingMapper.selectByServerIds(ipIds),
                        ResourceServerLandingDO::getServerId);

        List<XrayReconcileClientDTO> out = new ArrayList<>(running.size());
        for (XrayClientDO c : running) {
            ResourceServerDO landingSrv = landingSrvMap.get(c.getIpId());
            ResourceServerLandingDO landing = landingMap.get(c.getIpId());
            if (ObjectUtil.isNull(landingSrv) || ObjectUtil.isNull(landing)
                    || ObjectUtil.isNull(landing.getSocks5Port()) || ObjectUtil.isNull(landingSrv.getIpAddress())) {
                log.warn("[reconcile-desired] 跳过 client={} 落地机信息缺失 ip_id={}", c.getId(), c.getIpId());
                continue;
            }
            String outboundTag = XrayConstants.outboundTagOf(c.getId());
            String ruleTag = XrayConstants.ruleTagOf(c.getId());
            InboundUserSpec spec = InboundUserSpec.builder()
                    .email(c.getClientEmail())
                    .uuid(c.getClientUuid())
                    .protocol(cfg.getProtocol())
                    .flow("")
                    .build();

            XrayReconcileClientDTO dto = new XrayReconcileClientDTO();
            dto.setClientEmail(c.getClientEmail());
            dto.setClientUuid(c.getClientUuid());
            dto.setInboundTag(XrayConstants.SHARED_INBOUND_TAG);
            dto.setOutboundTag(outboundTag);
            dto.setRuleTag(ruleTag);
            dto.setAduJson(xrayInboundCli.buildUserOnlyInboundJson(XrayConstants.SHARED_INBOUND_TAG, spec));
            dto.setAdoJson(xrayOutboundCli.buildSocksOutboundJson(outboundTag, landingSrv.getIpAddress(),
                    landing.getSocks5Port(), landing.getSocks5Username(), landing.getSocks5Password()));
            dto.setAdrulesJson(xrayRoutingCli.buildAddRuleJson(ruleTag,
                    Collections.singletonList(c.getClientEmail()), outboundTag));
            out.add(dto);
        }
        return out;
    }
}
