package com.nook.biz.node.convert.xray.client;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.client.vo.ClientRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.framework.xray.grpc.UserTraffic;
import com.nook.common.web.response.PageResult;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** ClientDO 实体 ↔ VO 转换; clientUuid 在出参里 mask 不暴露明文. */
@Mapper
public interface XrayClientConvert {

    XrayClientConvert INSTANCE = Mappers.getMapper(XrayClientConvert.class);

    ClientRespVO convert(XrayClientDO entity);

    List<ClientRespVO> convertList(List<XrayClientDO> entities);

    default PageResult<ClientRespVO> convertPage(PageResult<XrayClientDO> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    /** 远端 traffic + 实体 → 出参 VO. */
    default ClientTrafficRespVO toTrafficVO(XrayClientDO e, UserTraffic t) {
        ClientTrafficRespVO vo = new ClientTrafficRespVO();
        vo.setInboundEntityId(e.getId());
        vo.setClientEmail(t.email());
        vo.setUpBytes(t.upBytes());
        vo.setDownBytes(t.downBytes());
        vo.setTotalBytes(t.totalBytes());
        vo.setExpiryEpochMillis(t.expiryEpochMillis());
        vo.setEnabled(t.enabled());
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
