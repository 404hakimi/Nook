package com.nook.biz.node.convert.xray;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.xray.dto.XrayInstallRespDTO;
import com.nook.biz.node.controller.xray.vo.XrayInboundConfigVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallRespVO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.framework.acme.IssuedCert;
import com.nook.biz.node.framework.xray.inbound.InboundProvisionResult;
import com.nook.biz.node.framework.xray.inbound.InboundSetupSpec;
import com.nook.biz.node.framework.xray.install.XrayDeployRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Map;

@Mapper
public interface XrayInstallConvert {

    XrayInstallConvert INSTANCE = Mappers.getMapper(XrayInstallConvert.class);

    XrayInstallRespVO convert(XrayInstallDO entity);

    XrayInstallRespDTO toRespDTO(XrayInstallDO entity);

    InboundSetupSpec toSetupSpec(XrayInboundConfigVO vo);

    // Boolean → boolean: 源为 null 时 MapStruct 不赋值, 目标保持 false (等价旧 Boolean.TRUE.equals); cert 为 null 时嵌套取值整体跳过
    @Mapping(target = "sharedInboundPort", source = "r.inbound.sharedInboundPort")
    @Mapping(target = "inboundConfigJson", source = "prov.inboundJson")
    @Mapping(target = "domain", source = "prov.fullDomain")
    @Mapping(target = "tlsCertPem", source = "cert.certPem")
    @Mapping(target = "tlsKeyPem", source = "cert.keyPem")
    // setTimezone 字段名以 set 开头, builder 方法被 MapStruct 当 JavaBeans setter 截成属性 timezone, 必须显式点名否则静默漏映射
    @Mapping(target = "timezone", source = "r.setTimezone")
    XrayDeployRequest toDeployRequest(String serverId, XrayInstallReqVO r,
                                      InboundProvisionResult prov, IssuedCert cert, int timeoutSeconds);

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
