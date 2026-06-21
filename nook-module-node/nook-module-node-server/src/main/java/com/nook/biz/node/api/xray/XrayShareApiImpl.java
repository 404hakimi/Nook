package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.api.xray.dto.ShareRenderReqDTO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.framework.xray.inbound.InboundParams;
import com.nook.biz.node.framework.xray.inbound.InboundParamsResolver;
import com.nook.biz.node.framework.xray.inbound.InboundProtocol;
import com.nook.biz.node.framework.xray.inbound.InboundProtocolFactory;
import com.nook.biz.node.framework.xray.inbound.ShareContext;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.mapper.XrayInboundMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xray 客户分享产物渲染 Api 实现; 批量解析线路机接入配置, 按协议分派到协议实现拼链接 / proxy
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayShareApiImpl implements XrayShareApi {

    /** 共享 inbound 端口缺省 (与客户端 vmess/vless 默认一致). */
    private static final int DEFAULT_PORT = 443;

    @Resource
    private XrayInboundMapper xrayInboundMapper;
    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private InboundProtocolFactory inboundProtocolFactory;

    @Override
    public Map<String, String> renderShareLinks(Collection<ShareRenderReqDTO> reqs) {
        return render(reqs, InboundProtocol::buildShareLink);
    }

    @Override
    public Map<String, Map<String, Object>> renderClashProxies(Collection<ShareRenderReqDTO> reqs) {
        return render(reqs, InboundProtocol::buildClashProxy);
    }

    /**
     * 批量渲染: 按 serverId 一次解析接入配置, 逐 req 备好 ShareContext 交协议实现渲染; 渲染返 null 的 reqKey 不入结果
     */
    private <T> Map<String, T> render(Collection<ShareRenderReqDTO> reqs, Renderer<T> renderer) {
        if (CollUtil.isEmpty(reqs)) {
            return Map.of();
        }
        Set<String> serverIds = reqs.stream().map(ShareRenderReqDTO::getServerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, ResolvedInbound> inboundMap = loadInbound(serverIds);
        Map<String, T> result = new HashMap<>(reqs.size());
        for (ShareRenderReqDTO req : reqs) {
            ResolvedInbound ri = inboundMap.get(req.getServerId());
            if (ri == null) {
                continue; // 线路机未装 xray / 协议形态未知
            }
            ShareContext ctx = ShareContext.builder()
                    .serverIp(ri.serverIp).port(ri.port).uuid(req.getUuid()).label(req.getLabel()).build();
            T rendered = renderer.render(ri.protocol, ri.params, ctx);
            if (rendered != null) {
                result.put(req.getReqKey(), rendered);
            }
        }
        return result;
    }

    /** 按 serverId 批量解出 {协议实现 + 语义参数 + 出网 IP + 端口}; 未知协议形态的行跳过. */
    private Map<String, ResolvedInbound> loadInbound(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return Map.of();
        }
        Map<String, XrayInboundDO> cfgMap = CollectionUtils.convertMap(
                xrayInboundMapper.selectBatchIds(serverIds), XrayInboundDO::getServerId);
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);
        Map<String, ResolvedInbound> result = new HashMap<>(cfgMap.size());
        for (Map.Entry<String, XrayInboundDO> entry : cfgMap.entrySet()) {
            XrayInboundDO cfg = entry.getValue();
            XrayInboundProtocolEnum proto = XrayInboundProtocolEnum.fromKey(cfg.getProtocolKey());
            if (proto == null) {
                // 未知协议形态 (枚举外的脏数据): 跳过该行其余照常渲染, 不让一行坏数据拖垮整份订阅; 告警避免无声吞掉
                log.warn("[renderShare] 跳过未知协议形态 server={} protocolKey={}", entry.getKey(), cfg.getProtocolKey());
                continue;
            }
            ResolvedInbound ri = new ResolvedInbound();
            ri.protocol = inboundProtocolFactory.resolveByProtocol(proto.getProtocol());
            ri.params = InboundParamsResolver.resolve(cfg.getProtocolKey(), cfg.getParams());
            ResourceServerDO srv = serverMap.get(entry.getKey());
            ri.serverIp = (srv == null) ? null : srv.getIpAddress();
            ri.port = (cfg.getSharedInboundPort() == null) ? DEFAULT_PORT : cfg.getSharedInboundPort();
            result.put(entry.getKey(), ri);
        }
        return result;
    }

    /** 单条渲染动作 (取链接 or 取 proxy); 由 buildShareLink / buildClashProxy 方法引用注入. */
    @FunctionalInterface
    private interface Renderer<T> {
        T render(InboundProtocol protocol, InboundParams params, ShareContext ctx);
    }

    /** 一台线路机解出的渲染输入. */
    private static class ResolvedInbound {
        private InboundProtocol protocol;
        private InboundParams params;
        private String serverIp;
        private int port;
    }
}
