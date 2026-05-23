package com.nook.biz.node.convert.xray;

import com.nook.biz.node.controller.xray.vo.XrayClientRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ClientDO ↔ VO 转换 + ipAddress / server / 共享 inbound 字段 enrich.
 *
 * <p>inbound 维度字段 (protocol / transport / listenIp / listenPort) 是 server 级共享配置, 存 xray_node;
 * controller 预拉 node map 在 convert 层 enrich, 跟 ipAddress / serverName 同套路.
 *
 * @author nook
 */
@Mapper
public interface XrayClientConvert {

    XrayClientConvert INSTANCE = Mappers.getMapper(XrayClientConvert.class);

    /** enrich 字段不在 DO 上, 由带 map 的重载补; 单独调本方法时这些字段留 null. */
    @Mapping(target = "ipAddress", ignore = true)
    @Mapping(target = "serverName", ignore = true)
    @Mapping(target = "serverHost", ignore = true)
    @Mapping(target = "protocol", ignore = true)
    @Mapping(target = "transport", ignore = true)
    @Mapping(target = "listenIp", ignore = true)
    @Mapping(target = "listenPort", ignore = true)
    XrayClientRespVO convert(XrayClientDO entity);

    List<XrayClientRespVO> convertList(List<XrayClientDO> entities);

    default XrayClientRespVO convert(XrayClientDO entity,
                                     Map<String, String> ipAddressMap,
                                     Map<String, ResourceServerDO> serverMap,
                                     Map<String, String> hostMap,
                                     Map<String, XrayNodeDO> nodeMap) {
        XrayClientRespVO vo = convert(entity);
        fillIpAddress(vo, ipAddressMap);
        fillServer(vo, serverMap, hostMap);
        fillInbound(vo, nodeMap);
        return vo;
    }

    default PageResult<XrayClientRespVO> convertPage(PageResult<XrayClientDO> page,
                                                     Map<String, String> ipAddressMap,
                                                     Map<String, ResourceServerDO> serverMap,
                                                     Map<String, String> hostMap,
                                                     Map<String, XrayNodeDO> nodeMap) {
        List<XrayClientRespVO> records = convertList(page.getRecords());
        for (XrayClientRespVO v : records) {
            fillIpAddress(v, ipAddressMap);
            fillServer(v, serverMap, hostMap);
            fillInbound(v, nodeMap);
        }
        return PageResult.of(page.getTotal(), records);
    }

    /** 从一批 DO 抽出去重 ipId 集合, 供 controller 一次性批量查 ipAddress. */
    static Set<String> collectIpIds(Collection<XrayClientDO> entities) {
        return CollectionUtils.convertSet(entities, XrayClientDO::getIpId);
    }

    /** 从一批 DO 抽出去重 serverId 集合, 供 controller 一次性批量查 server + xray_node. */
    static Set<String> collectServerIds(Collection<XrayClientDO> entities) {
        return CollectionUtils.convertSet(entities, XrayClientDO::getServerId);
    }

    private static void fillIpAddress(XrayClientRespVO vo, Map<String, String> ipAddressMap) {
        if (vo == null || ipAddressMap == null) return;
        String addr = ipAddressMap.get(vo.getIpId());
        if (addr != null) vo.setIpAddress(addr);
    }

    /** host 来自 resource_server_credential, 跟 name 分两 map 注入. */
    private static void fillServer(XrayClientRespVO vo,
                                   Map<String, ResourceServerDO> serverMap,
                                   Map<String, String> hostMap) {
        if (vo == null) return;
        if (serverMap != null) {
            ResourceServerDO s = serverMap.get(vo.getServerId());
            if (s != null) vo.setServerName(s.getName());
        }
        if (hostMap != null) {
            String h = hostMap.get(vo.getServerId());
            if (h != null) vo.setServerHost(h);
        }
    }

    /** 共享 inbound 是 server 级配置: 协议 / 传输 / 监听 IP / 监听端口 全部来自 xray_node. server 还没装 xray 时各字段留 null. */
    private static void fillInbound(XrayClientRespVO vo, Map<String, XrayNodeDO> nodeMap) {
        if (vo == null || nodeMap == null) return;
        XrayNodeDO node = nodeMap.get(vo.getServerId());
        if (node == null) return;
        vo.setProtocol(node.getProtocol());
        vo.setTransport(node.getTransport());
        vo.setListenIp(node.getListenIp());
        vo.setListenPort(node.getSharedInboundPort());
    }

}
