package com.nook.biz.node.convert.xray;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.xray.dto.XrayInstallRespDTO;
import com.nook.biz.node.controller.xray.vo.XrayInstallRespVO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.entity.ResourceServerDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.Map;

@Mapper
public interface XrayInstallConvert {

    XrayInstallConvert INSTANCE = Mappers.getMapper(XrayInstallConvert.class);

    XrayInstallRespVO convert(XrayInstallDO entity);

    XrayInstallRespDTO toRespDTO(XrayInstallDO entity);

    static void fillServer(XrayInstallRespVO vo,
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
}
