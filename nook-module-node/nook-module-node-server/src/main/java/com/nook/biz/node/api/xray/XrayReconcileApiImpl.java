package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.mapper.Socks5InstallMapper;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.mapper.XrayInboundMapper;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.inbound.InboundParams;
import com.nook.biz.node.framework.xray.inbound.InboundParamsResolver;
import com.nook.biz.node.framework.xray.inbound.InboundProtocolFactory;
import com.nook.biz.node.framework.xray.inbound.InboundUserRequest;
import com.nook.biz.node.framework.xray.inbound.vless.VlessRealityParams;
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

        // 协议形态由 protocol_key 解出, 流控从 inbound params 取 (跟订阅侧同源); reality 缺 flow 用户握手被拒
        String protocol = XrayInboundProtocolEnum.fromKey(cfg.getProtocolKey()).getProtocol();
        InboundParams params = InboundParamsResolver.resolve(cfg.getProtocolKey(), cfg.getParams());
        String inboundFlow = (params instanceof VlessRealityParams vless) ? vless.getFlow() : null;
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
                    .protocol(protocol)
                    .flow(inboundFlow)
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
            dto.setAdoJson(this.buildSocksOutboundJson(outboundTag, landingSrv.getIpAddress(),
                    landing.getSocks5Port(), landing.getSocks5Username(), landing.getSocks5Password()));
            dto.setAdrulesJson(this.buildAddRuleJson(ruleTag, List.of(cert.getAuthUser()), outboundTag));
            out.add(dto);
        }
        return out;
    }

    /**
     * 渲染 socks5 出站 JSON (ado 入参; 含可选鉴权); 出站对所有 inbound 协议一致, 非协议相关.
     *
     * <p>v25.10.15 起 outbound 要求扁平: settings 直接平铺 address / port / user / pass.
     */
    private String buildSocksOutboundJson(String tag, String host, int port, String username, String password) {
        JSONObject settings = new JSONObject();
        settings.put("address", host);
        settings.put("port", port);
        if (StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password)) {
            settings.put("user", username);
            settings.put("pass", password);
            settings.put("level", 0);
        }
        JSONObject outbound = new JSONObject();
        outbound.put("tag", tag);
        outbound.put("protocol", "socks");
        outbound.put("settings", settings);
        JSONArray outbounds = new JSONArray();
        outbounds.add(outbound);
        JSONObject config = new JSONObject();
        config.put("outbounds", outbounds);
        return config.toJSONString();
    }

    /** 渲染 adrules 入参 JSON: {"routing":{"rules":[{ruleTag,user,outboundTag}]}}; 路由对所有协议一致, 非协议相关. */
    private String buildAddRuleJson(String ruleTag, List<String> userEmails, String outboundTag) {
        JSONArray users = new JSONArray();
        users.addAll(userEmails);
        JSONObject rule = new JSONObject();
        rule.put("ruleTag", ruleTag);
        rule.put("user", users);
        rule.put("outboundTag", outboundTag);
        JSONArray rules = new JSONArray();
        rules.add(rule);
        JSONObject routing = new JSONObject();
        routing.put("rules", rules);
        JSONObject config = new JSONObject();
        config.put("routing", routing);
        return config.toJSONString();
    }
}
