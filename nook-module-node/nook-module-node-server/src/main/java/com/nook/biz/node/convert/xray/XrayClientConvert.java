package com.nook.biz.node.convert.xray;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.xray.vo.XrayClientRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
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
 * 客户端 DO ↔ VO 转换 + IP 地址 / 服务器 / 共享 inbound 字段回填.
 *
 * <p>inbound 维度字段 (协议 / 传输 / 监听 IP / 监听端口) 是服务器级共享配置, 由 controller 提前批量查 config map 后在此回填.
 *
 * @author nook
 */
@Mapper
public interface XrayClientConvert {

    XrayClientConvert INSTANCE = Mappers.getMapper(XrayClientConvert.class);

    /** 回填字段不在 DO 上, 由带 map 的重载补; 单独调本方法时这些字段留 null. */
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
                                     Map<String, XrayConfigDO> configMap) {
        XrayClientRespVO vo = convert(entity);
        fillIpAddress(vo, ipAddressMap);
        fillServer(vo, serverMap, hostMap);
        fillInbound(vo, configMap);
        return vo;
    }

    default PageResult<XrayClientRespVO> convertPage(PageResult<XrayClientDO> page,
                                                     Map<String, String> ipAddressMap,
                                                     Map<String, ResourceServerDO> serverMap,
                                                     Map<String, String> hostMap,
                                                     Map<String, XrayConfigDO> configMap) {
        List<XrayClientRespVO> records = convertList(page.getRecords());
        for (XrayClientRespVO v : records) {
            fillIpAddress(v, ipAddressMap);
            fillServer(v, serverMap, hostMap);
            fillInbound(v, configMap);
        }
        return PageResult.of(page.getTotal(), records);
    }

    /** 从一批 DO 抽出去重 ipId 集合, 供 controller 一次性批量查 ipAddress. */
    static Set<String> collectIpIds(Collection<XrayClientDO> entities) {
        return CollectionUtils.convertSet(entities, XrayClientDO::getIpId);
    }

    /** 从一批 DO 抽出去重 serverId 集合, 供 controller 一次性批量查 server + xray_config. */
    static Set<String> collectServerIds(Collection<XrayClientDO> entities) {
        return CollectionUtils.convertSet(entities, XrayClientDO::getServerId);
    }

    private static void fillIpAddress(XrayClientRespVO vo, Map<String, String> ipAddressMap) {
        if (ObjectUtil.isNull(vo) || ObjectUtil.isNull(ipAddressMap)) return;
        String addr = ipAddressMap.get(vo.getIpId());
        if (ObjectUtil.isNotNull(addr)) vo.setIpAddress(addr);
    }

    /** host 与 name 分两 map 注入. */
    private static void fillServer(XrayClientRespVO vo,
                                   Map<String, ResourceServerDO> serverMap,
                                   Map<String, String> hostMap) {
        if (ObjectUtil.isNull(vo)) return;
        if (ObjectUtil.isNotNull(serverMap)) {
            ResourceServerDO s = serverMap.get(vo.getServerId());
            if (ObjectUtil.isNotNull(s)) vo.setServerName(s.getName());
        }
        if (ObjectUtil.isNotNull(hostMap)) {
            String h = hostMap.get(vo.getServerId());
            if (ObjectUtil.isNotNull(h)) vo.setServerHost(h);
        }
    }

    /** 共享 inbound 是服务器级配置: 协议 / 传输 / 监听 IP / 监听端口 全部来自服务器级共享配置; 未装 xray 时各字段留 null. */
    private static void fillInbound(XrayClientRespVO vo, Map<String, XrayConfigDO> configMap) {
        if (ObjectUtil.isNull(vo) || ObjectUtil.isNull(configMap)) return;
        XrayConfigDO cfg = configMap.get(vo.getServerId());
        if (ObjectUtil.isNull(cfg)) return;
        vo.setProtocol(cfg.getProtocol());
        vo.setTransport(cfg.getTransport());
        vo.setListenIp(cfg.getListenIp());
        vo.setListenPort(cfg.getSharedInboundPort());
    }

}
