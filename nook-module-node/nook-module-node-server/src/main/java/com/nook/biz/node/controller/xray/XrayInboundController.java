package com.nook.biz.node.controller.xray;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.xray.vo.XrayInboundRespVO;
import com.nook.biz.node.convert.xray.XrayInboundConvert;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.framework.xray.inbound.InboundParams;
import com.nook.biz.node.framework.xray.inbound.InboundParamsResolver;
import com.nook.biz.node.framework.xray.inbound.InboundProtocol;
import com.nook.biz.node.framework.xray.inbound.InboundProtocolFactory;
import com.nook.biz.node.service.xray.config.XrayInboundService;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - Xray inbound 共享配置 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/xray/inbound")
@Validated
public class XrayInboundController {

    @Resource
    private XrayInboundService xrayInboundService;
    @Resource
    private InboundProtocolFactory inboundProtocolFactory;
    @Resource
    private XrayInstallService xrayInstallService;

    /**
     * 获得 inbound 共享配置 (未装机时返 null); 协议字段值经协议 formPrefill 投影成 formValues, 供前端重装预填
     *
     * @param serverId 服务器编号
     * @return inbound 共享配置
     */
    @GetMapping("/get-xray-inbound")
    public Result<XrayInboundRespVO> getXrayInbound(@RequestParam("serverId") String serverId) {
        XrayInboundDO entity = xrayInboundService.get(serverId);
        if (ObjectUtil.isNull(entity)) {
            return Result.ok(null);
        }
        XrayInboundRespVO vo = XrayInboundConvert.INSTANCE.convert(entity);
        InboundParams params = InboundParamsResolver.resolve(entity.getProtocolKey(), entity.getParams());
        InboundProtocol protocol = inboundProtocolFactory.resolveByProtocol(vo.getProtocol());
        // 域名绑定 (domainId/subdomain) 在 xray_install 非 params, 一并取来给 formPrefill
        XrayInstallDO install = xrayInstallService.get(serverId);
        vo.setFormValues(protocol.formPrefill(params,
                install == null ? null : install.getDomainId(),
                install == null ? null : install.getSubdomain()));
        return Result.ok(vo);
    }
}
