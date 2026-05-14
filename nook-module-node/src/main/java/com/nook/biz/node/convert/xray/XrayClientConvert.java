package com.nook.biz.node.convert.xray;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.vo.XrayClientRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.common.web.response.PageResult;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ClientDO ↔ VO 转换; 纯转换, 不依赖 Api / Service.
 *
 * @author nook
 */
@Mapper
public interface XrayClientConvert {

    XrayClientConvert INSTANCE = Mappers.getMapper(XrayClientConvert.class);

    /** ipAddress / serverName / serverHost 不在 DO 上, 由带 map 的重载补; 单独调本方法时这些字段留 null. */
    @Mapping(target = "ipAddress", ignore = true)
    @Mapping(target = "serverName", ignore = true)
    @Mapping(target = "serverHost", ignore = true)
    XrayClientRespVO convert(XrayClientDO entity);

    List<XrayClientRespVO> convertList(List<XrayClientDO> entities);

    default XrayClientRespVO convert(XrayClientDO entity,
                                     Map<String, String> ipAddressMap,
                                     Map<String, ResourceServerDO> serverMap) {
        XrayClientRespVO vo = convert(entity);
        fillIpAddress(vo, ipAddressMap);
        fillServer(vo, serverMap);
        return vo;
    }

    default PageResult<XrayClientRespVO> convertPage(PageResult<XrayClientDO> page,
                                                     Map<String, String> ipAddressMap,
                                                     Map<String, ResourceServerDO> serverMap) {
        List<XrayClientRespVO> records = convertList(page.getRecords());
        for (XrayClientRespVO v : records) {
            fillIpAddress(v, ipAddressMap);
            fillServer(v, serverMap);
        }
        return PageResult.of(page.getTotal(), records);
    }

    /** 从一批 DO 抽出去重 ipId 集合, 供 controller 做一次性批量查 ipAddress. */
    static Set<String> collectIpIds(Collection<XrayClientDO> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptySet();
        return entities.stream()
                .map(XrayClientDO::getIpId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    /** 从一批 DO 抽出去重 serverId 集合, 供 controller 做一次性批量查 server. */
    static Set<String> collectServerIds(Collection<XrayClientDO> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptySet();
        return entities.stream()
                .map(XrayClientDO::getServerId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    /** list / detail 出参 mask UUID; 留前 8 后 4 让管理员粗略对得上. */
    @AfterMapping
    default void maskSensitive(XrayClientDO src, @MappingTarget XrayClientRespVO target) {
        target.setClientUuid(maskUuid(src.getClientUuid()));
    }

    private static void fillIpAddress(XrayClientRespVO vo, Map<String, String> ipAddressMap) {
        if (vo == null || ipAddressMap == null) return;
        String addr = ipAddressMap.get(vo.getIpId());
        if (addr != null) vo.setIpAddress(addr);
    }

    private static void fillServer(XrayClientRespVO vo, Map<String, ResourceServerDO> serverMap) {
        if (vo == null || serverMap == null) return;
        ResourceServerDO s = serverMap.get(vo.getServerId());
        if (s != null) {
            vo.setServerName(s.getName());
            vo.setServerHost(s.getHost());
        }
    }

    private static String maskUuid(String uuid) {
        if (StrUtil.isBlank(uuid)) return uuid;
        if (uuid.length() <= 12) return "***";
        return uuid.substring(0, 8) + "***" + uuid.substring(uuid.length() - 4);
    }
}
