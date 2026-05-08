package com.nook.biz.xray.convert;

import com.nook.biz.xray.backend.dto.XrayClientRef;
import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundRespVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundTrafficRespVO;
import com.nook.biz.xray.entity.XrayInbound;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface XrayInboundConvert {

    XrayInboundConvert INSTANCE = Mappers.getMapper(XrayInboundConvert.class);

    XrayInboundRespVO convert(XrayInbound entity);

    List<XrayInboundRespVO> convertList(List<XrayInbound> entities);

    default PageResult<XrayInboundRespVO> convertPage(PageResult<XrayInbound> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    /** 实体 → backend 调用用的 client 引用三件套。 */
    default XrayClientRef toRef(XrayInbound e) {
        if (e == null) return null;
        return new XrayClientRef(e.getExternalInboundRef(), e.getClientUuid(), e.getClientEmail());
    }

    /** backend traffic + 实体 → 出参 VO。 */
    default XrayInboundTrafficRespVO toTrafficVO(XrayInbound e, XrayClientTraffic t) {
        XrayInboundTrafficRespVO vo = new XrayInboundTrafficRespVO();
        vo.setInboundEntityId(e.getId());
        vo.setClientEmail(t.email());
        vo.setUpBytes(t.upBytes());
        vo.setDownBytes(t.downBytes());
        vo.setTotalBytes(t.totalBytes());
        vo.setExpiryEpochMillis(t.expiryEpochMillis());
        vo.setEnabled(t.enabled());
        return vo;
    }
}
