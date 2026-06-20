package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.mapper.Socks5InstallMapper;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.mapper.XrayInboundMapper;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.inbound.InboundProtocolFactory;
import com.nook.biz.node.framework.xray.inbound.InboundUserRequest;
import com.nook.biz.node.framework.xray.outbound.XrayOutboundRenderer;
import com.nook.biz.node.framework.xray.routing.XrayRoutingRenderer;
import com.nook.biz.trade.api.SubscriptionCertApi;
import com.nook.biz.trade.api.dto.SubscriptionCertRespDTO;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link XrayReconcileApi} 实现; 从订阅凭证拼某线路机应运行的全部接入点期望态 (含预先拼好的下发请求 JSON: 加用户 / 加出站 / 加路由).
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayReconcileApiImpl implements XrayReconcileApi {

    @Resource
    private XrayInboundMapper xrayInboundMapper;
    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private Socks5InstallMapper socks5InstallMapper;
    @Resource
    private SubscriptionCertApi subscriptionCertApi;
    @Resource
    private InboundProtocolFactory inboundProtocolFactory;
    @Resource
    private XrayOutboundRenderer xrayOutboundRenderer;
    @Resource
    private XrayRoutingRenderer xrayRoutingRenderer;

    @Override
    public List<XrayReconcileClientDTO> getDesiredClients(String serverId) {
        // 该线路机未装 xray (无共享 inbound 配置) → 没有任何接入点能挂上去, 直接空
        XrayInboundDO cfg = xrayInboundMapper.selectById(serverId);
        if (ObjectUtil.isNull(cfg)) {
            return List.of();
        }
        // 凭证已按 server_id + 应运行(ACTIVE) 过滤; 停服/已吊销的不下发, 让 agent 把它们从远端收敛掉
        List<SubscriptionCertRespDTO> certs = subscriptionCertApi.listActiveByServerInGroup(serverId);
        if (CollUtil.isEmpty(certs)) {
            return List.of();
        }
        // 落地机主表(出网IP) + 落地子表(socks 凭据): 按 ip_id 批量各查一次, 避免逐接入点查 (N+1)
        Set<String> ipIds = new HashSet<>(certs.size());
        for (SubscriptionCertRespDTO cert : certs) {
            if (ObjectUtil.isNotNull(cert.getIpId())) {
                ipIds.add(cert.getIpId());
            }
        }
        Map<String, ResourceServerDO> landingSrvMap = CollUtil.isEmpty(ipIds) ? Map.of()
                : CollectionUtils.convertMap(resourceServerMapper.selectBatchIds(ipIds), ResourceServerDO::getId);
        Map<String, Socks5InstallDO> landingMap = CollUtil.isEmpty(ipIds) ? Map.of()
                : CollectionUtils.convertMap(socks5InstallMapper.selectByServerIds(ipIds),
                        Socks5InstallDO::getServerId);

        // 协议形态由 protocol_key 解出, 仅用于分派到对应协议实现; 协议特定细节 (如 vless flow) 全在实现里, 此处不碰
        String protocol = XrayInboundProtocolEnum.fromKey(cfg.getProtocolKey()).getProtocol();
        List<XrayReconcileClientDTO> out = new ArrayList<>(certs.size());
        for (SubscriptionCertRespDTO cert : certs) {
            ResourceServerDO landingSrv = landingSrvMap.get(cert.getIpId());
            Socks5InstallDO landing = landingMap.get(cert.getIpId());
            // 落地机出网IP / socks 凭据任一缺失就拼不出出站, 跳过这一个接入点而不是让整轮对账失败
            if (ObjectUtil.isNull(landingSrv) || ObjectUtil.isNull(landing)
                    || ObjectUtil.isNull(landing.getSocks5Port()) || ObjectUtil.isNull(landingSrv.getIpAddress())) {
                log.warn("[reconcile-desired] 跳过 cert={} 落地机信息缺失 ip_id={}", cert.getId(), cert.getIpId());
                continue;
            }
            // 出站 / 路由 tag 按凭证 id 取, 一个接入点一套, 故障切换/轮换都不变 → agent 据 tag 比对增删
            String outboundTag = XrayConstants.outboundTagOf(cert.getId(), cert.getIpId());
            String ruleTag = XrayConstants.ruleTagOf(cert.getId(), cert.getIpId());
            InboundUserRequest userReq = InboundUserRequest.builder()
                    .email(cert.getAuthUser())
                    .uuid(cert.getAuthSecret())
                    .build();

            // 期望态三件套, agent 据此把该接入点装起来: ① 共享 inbound 加该用户 ② 加它专属 socks 出站(走落地机) ③ 加路由把该用户流量导向该出站
            XrayReconcileClientDTO dto = new XrayReconcileClientDTO();
            dto.setClientEmail(cert.getAuthUser());
            dto.setClientUuid(cert.getAuthSecret());
            dto.setInboundTag(XrayConstants.SHARED_INBOUND_TAG);
            dto.setOutboundTag(outboundTag);
            dto.setRuleTag(ruleTag);
            // 协议特定的加用户 JSON 由对应 InboundProtocol 实现渲染 (vmess/vless clients[] 字段不同)
            dto.setAduJson(inboundProtocolFactory.resolveByProtocol(protocol)
                    .buildAduJson(XrayConstants.SHARED_INBOUND_TAG, userReq));
            dto.setAdoJson(xrayOutboundRenderer.socksOutboundJson(outboundTag, landingSrv.getIpAddress(),
                    landing.getSocks5Port(), landing.getSocks5Username(), landing.getSocks5Password()));
            dto.setAdrulesJson(xrayRoutingRenderer.addRuleJson(ruleTag, List.of(cert.getAuthUser()), outboundTag));
            out.add(dto);
        }
        return out;
    }
}
