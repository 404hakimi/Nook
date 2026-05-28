package com.nook.biz.node.api.xray;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link XrayClientReconcileApi} 实现; 从 DB 拼某线路机应存在的全部客户端期望态 (含预拼 adu/ado/adrules JSON).
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XrayClientReconcileApiImpl implements XrayClientReconcileApi {

    private final XrayClientMapper clientMapper;
    private final XrayConfigMapper configMapper;
    private final ResourceServerMapper serverMapper;
    private final ResourceServerLandingMapper landingMapper;
    private final XrayInboundCli inboundCli;
    private final XrayOutboundCli outboundCli;
    private final XrayRoutingCli routingCli;

    @Override
    public List<XrayReconcileClientDTO> getDesiredClients(String serverId) {
        XrayConfigDO cfg = configMapper.selectById(serverId);
        if (cfg == null) {
            return Collections.emptyList();
        }
        List<XrayClientDO> clients = clientMapper.selectByServerId(serverId);
        List<XrayReconcileClientDTO> out = new ArrayList<>(clients.size());
        for (XrayClientDO c : clients) {
            if (!XrayClientStatusEnum.RUNNING.matches(c.getStatus())) {
                continue;
            }
            ResourceServerDO landingSrv = serverMapper.selectById(c.getIpId());
            ResourceServerLandingDO landing = landingMapper.selectByServerId(c.getIpId());
            if (landingSrv == null || landing == null
                    || landing.getSocks5Port() == null || landingSrv.getIpAddress() == null) {
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
                    .totalBytes(c.getTotalBytes() == null ? 0L : c.getTotalBytes())
                    .expiryEpochMillis(c.getExpiryEpochMillis() == null ? 0L : c.getExpiryEpochMillis())
                    .limitIp(c.getLimitIp() == null ? 0 : c.getLimitIp())
                    .build();

            XrayReconcileClientDTO dto = new XrayReconcileClientDTO();
            dto.setClientEmail(c.getClientEmail());
            dto.setInboundTag(XrayConstants.SHARED_INBOUND_TAG);
            dto.setOutboundTag(outboundTag);
            dto.setRuleTag(ruleTag);
            dto.setAduJson(inboundCli.buildUserOnlyInboundJson(XrayConstants.SHARED_INBOUND_TAG, spec));
            dto.setAdoJson(outboundCli.buildSocksOutboundJson(outboundTag, landingSrv.getIpAddress(),
                    landing.getSocks5Port(), landing.getSocks5Username(), landing.getSocks5Password()));
            dto.setAdrulesJson(routingCli.buildAddRuleJson(ruleTag,
                    Collections.singletonList(c.getClientEmail()), outboundTag));
            out.add(dto);
        }
        return out;
    }
}
