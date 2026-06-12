package com.nook.biz.node.controller.xray;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.xray.vo.XrayInboundRespVO;
import com.nook.biz.node.convert.xray.XrayInboundConvert;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.service.xray.config.XrayInboundService;
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

    /**
     * 获得 inbound 共享配置 (未装机时返 null)
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
        return Result.ok(XrayInboundConvert.INSTANCE.convert(entity));
    }
}
