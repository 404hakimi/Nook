package com.nook.biz.node.convert.xray;

import com.nook.biz.node.controller.xray.vo.XrayClientTrafficRespVO;
import com.nook.biz.node.convert.xray.format.BytesFormatter;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Xray 客户端流量 VO 转换; 纯转换, 不依赖 Api / Service.
 *
 * <p>从 {@link XrayClientConvert} 拆出来, 让"流量域"convert 与"实体域"convert 各自独立,
 * 跟 service 层 {@link com.nook.biz.node.service.xray.client.XrayClientTrafficService} 边界对齐.
 *
 * @author nook
 */
@Mapper
public interface XrayClientTrafficConvert {

    XrayClientTrafficConvert INSTANCE = Mappers.getMapper(XrayClientTrafficConvert.class);

    /** 合并 traffic 快照 + 实体到出参 VO; 字节字段同时下发原值 + 人读字符串. */
    default XrayClientTrafficRespVO toTrafficVO(XrayClientDO e, XrayUserTrafficSnapshot t) {
        long up = t.getUpBytes();
        long down = t.getDownBytes();
        long total = t.getTotalBytes();
        long used = up + down;

        XrayClientTrafficRespVO vo = new XrayClientTrafficRespVO();
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
}
