package com.nook.biz.node.convert.xray;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.vo.XraySlotItemRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.slot.XraySlotPoolDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xray Slot 池 Convert; 单行 DO → VO + 视图列表组装 (注入 portBase / clientMap 派生字段)
 *
 * @author nook
 */
@Mapper
public interface XraySlotPoolConvert {

    XraySlotPoolConvert INSTANCE = Mappers.getMapper(XraySlotPoolConvert.class);

    /** 单行映射; listenPort / clientEmail 等派生字段由上层 view 方法回填 */
    @Mapping(target = "clientId", source = "usedBy")
    @Mapping(target = "listenPort", ignore = true)
    @Mapping(target = "clientEmail", ignore = true)
    @Mapping(target = "protocol", ignore = true)
    @Mapping(target = "transport", ignore = true)
    @Mapping(target = "clientStatus", ignore = true)
    XraySlotItemRespVO convert(XraySlotPoolDO entity);

    /**
     * Slot 占用视图组装
     *
     * @param slots     原始 slot 行 (xray_slot_pool 全部 50 行)
     * @param portBase  xray_node.slot_port_base; 派生 listenPort 用
     * @param clientMap 占用此 slot 的 client 行 (按 usedBy id key); 已物理删的 client 不在 map 内
     * @return 视图行列表, 与 slots 等长 + 顺序一致
     */
    default List<XraySlotItemRespVO> convertSlotView(List<XraySlotPoolDO> slots,
                                                     int portBase,
                                                     Map<String, XrayClientDO> clientMap) {
        if (slots == null || slots.isEmpty()) return Collections.emptyList();
        List<XraySlotItemRespVO> view = new ArrayList<>(slots.size());
        for (XraySlotPoolDO s : slots) {
            XraySlotItemRespVO item = convert(s);
            item.setListenPort(portBase + s.getSlotIndex());
            fillClient(item, clientMap);
            view.add(item);
        }
        return view;
    }

    /** 抽 used_by 去重集合, 供 controller 一次性批量查 client. */
    static Set<String> collectUsedByIds(Collection<XraySlotPoolDO> slots) {
        if (slots == null || slots.isEmpty()) return Collections.emptySet();
        return slots.stream()
                .map(XraySlotPoolDO::getUsedBy)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    private static void fillClient(XraySlotItemRespVO vo, Map<String, XrayClientDO> clientMap) {
        if (vo == null || clientMap == null || vo.getClientId() == null) return;
        XrayClientDO c = clientMap.get(vo.getClientId());
        if (c == null) return;
        vo.setClientEmail(c.getClientEmail());
        vo.setProtocol(c.getProtocol());
        vo.setTransport(c.getTransport());
        vo.setClientStatus(c.getStatus());
    }
}
