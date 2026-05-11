package com.nook.biz.node.convert.xray.client;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.client.vo.ClientRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
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

    /** ipAddress 不在 DO 上, 由带 map 的重载补; 单独调本方法时 ipAddress 留 null. */
    @Mapping(target = "ipAddress", ignore = true)
    ClientRespVO convert(XrayClientDO entity);

    List<ClientRespVO> convertList(List<XrayClientDO> entities);

    default ClientRespVO convert(XrayClientDO entity, Map<String, String> ipAddressMap) {
        ClientRespVO vo = convert(entity);
        fillIpAddress(vo, ipAddressMap);
        return vo;
    }

    default PageResult<ClientRespVO> convertPage(PageResult<XrayClientDO> page,
                                                 Map<String, String> ipAddressMap) {
        List<ClientRespVO> records = convertList(page.getRecords());
        for (ClientRespVO v : records) {
            fillIpAddress(v, ipAddressMap);
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

    /** 合并 traffic 快照 + 实体到出参 VO; 字节字段同时下发原值 + 人读字符串. */
    default ClientTrafficRespVO toTrafficVO(XrayClientDO e, XrayUserTrafficSnapshot t) {
        long up = t.getUpBytes();
        long down = t.getDownBytes();
        long total = t.getTotalBytes();
        long used = up + down;

        ClientTrafficRespVO vo = new ClientTrafficRespVO();
        vo.setInboundEntityId(e.getId());
        vo.setClientEmail(t.getEmail());
        vo.setUpBytes(up);
        vo.setUpBytesText(BytesFormatter.human(up));
        vo.setDownBytes(down);
        vo.setDownBytesText(BytesFormatter.human(down));
        vo.setUsedBytes(used);
        vo.setUsedBytesText(BytesFormatter.human(used));
        vo.setTotalBytes(total);
        vo.setTotalBytesText(total > 0 ? BytesFormatter.human(total) : "无限制");
        // total=0 表示不限, 百分比无意义返 null; total>0 时 cap 100 防超额显示 120%
        vo.setUsagePct(total > 0 ? (int) Math.min(100L, Math.round(used * 100.0 / total)) : null);
        vo.setExpiryEpochMillis(t.getExpiryEpochMillis());
        vo.setEnabled(t.isEnabled());
        return vo;
    }

    /** list / detail 出参 mask UUID; 留前 8 后 4 让管理员粗略对得上. */
    @AfterMapping
    default void maskSensitive(XrayClientDO src, @MappingTarget ClientRespVO target) {
        target.setClientUuid(maskUuid(src.getClientUuid()));
    }

    private static void fillIpAddress(ClientRespVO vo, Map<String, String> ipAddressMap) {
        if (vo == null || ipAddressMap == null) return;
        String addr = ipAddressMap.get(vo.getIpId());
        if (addr != null) vo.setIpAddress(addr);
    }

    private static String maskUuid(String uuid) {
        if (StrUtil.isBlank(uuid)) return uuid;
        if (uuid.length() <= 12) return "***";
        return uuid.substring(0, 8) + "***" + uuid.substring(uuid.length() - 4);
    }
}
