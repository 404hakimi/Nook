package com.nook.biz.xray.convert;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.xray.backend.dto.XrayClientRef;
import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundRespVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundTrafficRespVO;
import com.nook.biz.xray.entity.XrayInbound;
import com.nook.common.web.response.PageResult;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** XrayInbound 实体 ↔ VO/Ref 转换；clientUuid 在出参里 mask, 不暴露明文。 */
@Mapper
public interface XrayInboundConvert {

    XrayInboundConvert INSTANCE = Mappers.getMapper(XrayInboundConvert.class);

    XrayInboundRespVO convert(XrayInbound entity);

    List<XrayInboundRespVO> convertList(List<XrayInbound> entities);

    default PageResult<XrayInboundRespVO> convertPage(PageResult<XrayInbound> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    /** 实体 → backend 调用用的 client 引用三件套(模块内部用，含明文 UUID)。 */
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

    /**
     * 出参 mask UUID。规范 §11 把 UUID 列为凭据，不能在 list/detail API 里明文下发。
     * 留前 8 位与后 4 位让管理员粗略对得上，中间替成 ***。
     */
    @AfterMapping
    default void maskSensitive(XrayInbound src, @MappingTarget XrayInboundRespVO target) {
        target.setClientUuid(maskUuid(src.getClientUuid()));
    }

    private static String maskUuid(String uuid) {
        if (StrUtil.isBlank(uuid)) return uuid;
        if (uuid.length() <= 12) return "***";
        return uuid.substring(0, 8) + "***" + uuid.substring(uuid.length() - 4);
    }
}
