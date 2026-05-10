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

import java.util.List;

/** ClientDO 实体 ↔ VO 转换; clientUuid 在出参里 mask 不暴露明文. */
@Mapper
public interface XrayClientConvert {

    XrayClientConvert INSTANCE = Mappers.getMapper(XrayClientConvert.class);

    /**
     * ipAddress 不在 DO 上, 由 controller 调 service.enrichIpAddress 批量补,
     * 这里显式 ignore 以静默 mapstruct "Unmapped target property" 警告.
     */
    @Mapping(target = "ipAddress", ignore = true)
    ClientRespVO convert(XrayClientDO entity);

    List<ClientRespVO> convertList(List<XrayClientDO> entities);

    default PageResult<ClientRespVO> convertPage(PageResult<XrayClientDO> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    /**
     * 把远端 traffic 快照 + 实体合并成出参 VO; 字节字段同时下发原值 + 人读字符串, 前端零计算.
     * usagePct: totalBytes=0 时返 null (无限制场景), 让前端 v-if 直接判断.
     *
     * @param e 客户端实体
     * @param t 远端流量快照
     * @return ClientTrafficRespVO
     */
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
        // 上限 0 = 不限, 百分比无意义返 null; 上限 > 0 时 cap 到 100 防止超额时显示 120%
        vo.setUsagePct(total > 0 ? (int) Math.min(100L, Math.round(used * 100.0 / total)) : null);
        vo.setExpiryEpochMillis(t.getExpiryEpochMillis());
        vo.setEnabled(t.isEnabled());
        return vo;
    }

    /** 出参 mask UUID (规范 §11 凭据不在 list/detail 明文下发); 留前 8 后 4 让管理员粗略对得上. */
    @AfterMapping
    default void maskSensitive(XrayClientDO src, @MappingTarget ClientRespVO target) {
        target.setClientUuid(maskUuid(src.getClientUuid()));
    }

    private static String maskUuid(String uuid) {
        if (StrUtil.isBlank(uuid)) return uuid;
        if (uuid.length() <= 12) return "***";
        return uuid.substring(0, 8) + "***" + uuid.substring(uuid.length() - 4);
    }
}
