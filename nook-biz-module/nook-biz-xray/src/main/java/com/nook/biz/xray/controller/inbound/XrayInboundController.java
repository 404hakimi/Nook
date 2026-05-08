package com.nook.biz.xray.controller.inbound;

import com.nook.biz.xray.controller.inbound.vo.XrayInboundPageReqVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundProvisionReqVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundRespVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundTrafficRespVO;
import com.nook.biz.xray.convert.XrayInboundConvert;
import com.nook.biz.xray.entity.XrayInbound;
import com.nook.biz.xray.service.XrayInboundService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 inbound (client 配置) 管理。
 * 业务流程触发的 provision 走 {@link com.nook.biz.xray.api.XrayInboundApi}（订阅成功后调用）；
 * 这里是运营/调试用的手动接口。
 */
@RestController
@RequestMapping("/admin/xray/inbounds")
@RequiredArgsConstructor
@Validated
public class XrayInboundController {

    private final XrayInboundService xrayInboundService;

    @GetMapping
    public Result<PageResult<XrayInboundRespVO>> page(@ModelAttribute XrayInboundPageReqVO reqVO) {
        return Result.ok(XrayInboundConvert.INSTANCE.convertPage(xrayInboundService.page(reqVO)));
    }

    @GetMapping("/{id}")
    public Result<XrayInboundRespVO> detail(@PathVariable @NotBlank String id) {
        return Result.ok(XrayInboundConvert.INSTANCE.convert(xrayInboundService.findById(id)));
    }

    @PostMapping("/provision")
    public Result<XrayInboundRespVO> provision(@RequestBody @Valid XrayInboundProvisionReqVO reqVO) {
        XrayInbound e = xrayInboundService.provision(reqVO);
        return Result.ok(XrayInboundConvert.INSTANCE.convert(e));
    }

    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable @NotBlank String id) {
        xrayInboundService.revoke(id);
        return Result.ok();
    }

    @PostMapping("/{id}/rotate")
    public Result<XrayInboundRespVO> rotate(@PathVariable @NotBlank String id) {
        return Result.ok(XrayInboundConvert.INSTANCE.convert(xrayInboundService.rotate(id)));
    }

    @GetMapping("/{id}/traffic")
    public Result<XrayInboundTrafficRespVO> traffic(@PathVariable @NotBlank String id) {
        XrayInbound e = xrayInboundService.findById(id);
        return Result.ok(XrayInboundConvert.INSTANCE.toTrafficVO(e, xrayInboundService.getTraffic(id)));
    }

    @PostMapping("/{id}/reset-traffic")
    public Result<Void> resetTraffic(@PathVariable @NotBlank String id) {
        xrayInboundService.resetTraffic(id);
        return Result.ok();
    }
}
