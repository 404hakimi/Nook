package com.nook.biz.node.convert.xray.client;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.client.vo.ClientRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.resource.api.ResourceIpPoolApi;
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

/** ClientDO 实体 ↔ VO 转换; clientUuid 在出参里 mask 不暴露明文. */
@Mapper
public interface XrayClientConvert {

    XrayClientConvert INSTANCE = Mappers.getMapper(XrayClientConvert.class);

    /**
     * ipAddress 在 DO 上不存在, 由带 ipPoolApi 重载方法补; 此处 ignore 静默 mapstruct 警告.
     *
     * @param entity 客户端实体
     * @return ClientRespVO (ipAddress 字段为 null)
     */
    @Mapping(target = "ipAddress", ignore = true)
    ClientRespVO convert(XrayClientDO entity);

    List<ClientRespVO> convertList(List<XrayClientDO> entities);

    default PageResult<ClientRespVO> convertPage(PageResult<XrayClientDO> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }

    /**
     * 转 + 一次性 enrich ipAddress; 单条 detail / provision / rotate 出参用.
     *
     * @param entity     客户端实体
     * @param ipPoolApi  Resource 模块跨模块 API, 用于按 ipId 批量查 ipAddress
     * @return ClientRespVO (ipAddress 已填; 已删 IP 留 null 由前端 fallback)
     */
    default ClientRespVO convert(XrayClientDO entity, ResourceIpPoolApi ipPoolApi) {
        ClientRespVO vo = convert(entity);
        enrichIpAddress(Collections.singletonList(vo), ipPoolApi);
        return vo;
    }

    /**
     * 转 + 一次性 enrich ipAddress; 列表 page 出参用.
     *
     * @param page       service 层返回的实体分页
     * @param ipPoolApi  Resource 模块跨模块 API
     * @return PageResult VO 分页 (records 全部已 enrich)
     */
    default PageResult<ClientRespVO> convertPage(PageResult<XrayClientDO> page, ResourceIpPoolApi ipPoolApi) {
        PageResult<ClientRespVO> vo = convertPage(page);
        enrichIpAddress(vo.getRecords(), ipPoolApi);
        return vo;
    }

    /**
     * 收集 vos 里的 ipId 一次批量查 ipAddress, 填回各 vo.
     *
     * @param vos        待 enrich 的 VO 集合
     * @param ipPoolApi  Resource 模块跨模块 API
     */
    default void enrichIpAddress(Collection<ClientRespVO> vos, ResourceIpPoolApi ipPoolApi) {
        if (vos == null || vos.isEmpty()) return;
        Set<String> ipIds = vos.stream()
                .map(ClientRespVO::getIpId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (ipIds.isEmpty()) return;
        Map<String, String> map = ipPoolApi.loadIpAddressMap(ipIds);
        for (ClientRespVO v : vos) {
            String addr = map.get(v.getIpId());
            if (addr != null) v.setIpAddress(addr);
        }
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
