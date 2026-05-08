package com.nook.biz.xray.controller.inbound;

import com.nook.biz.xray.controller.inbound.vo.XrayInboundPageReqVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundProvisionReqVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundRespVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundTrafficRespVO;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundUpdateReqVO;
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
import org.springframework.web.bind.annotation.PutMapping;
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

    /** 分页列 client 配置；过滤条件见 PageReqVO。 */
    @GetMapping
    public Result<PageResult<XrayInboundRespVO>> page(@ModelAttribute XrayInboundPageReqVO reqVO) {
        return Result.ok(XrayInboundConvert.INSTANCE.convertPage(xrayInboundService.page(reqVO)));
    }

    /** 详情；同样会被 mask UUID。 */
    @GetMapping("/{id}")
    public Result<XrayInboundRespVO> detail(@PathVariable @NotBlank String id) {
        return Result.ok(XrayInboundConvert.INSTANCE.convert(xrayInboundService.findById(id)));
    }

    /** 手动开通；远端 backend.addClient 成功后才落 DB。重复 (memberId, ipId) 抛 CLIENT_DUPLICATE。 */
    @PostMapping("/provision")
    public Result<XrayInboundRespVO> provision(@RequestBody @Valid XrayInboundProvisionReqVO reqVO) {
        XrayInbound e = xrayInboundService.provision(reqVO);
        return Result.ok(XrayInboundConvert.INSTANCE.convert(e));
    }

    /** 编辑本地元数据(listenIp/listenPort/transport/status)；不触达远端 backend。 */
    @PutMapping("/{id}")
    public Result<XrayInboundRespVO> update(@PathVariable @NotBlank String id,
                                            @RequestBody @Valid XrayInboundUpdateReqVO reqVO) {
        return Result.ok(XrayInboundConvert.INSTANCE.convert(xrayInboundService.update(id, reqVO)));
    }

    /** 吊销；远端先删 client 再软删 DB；远端 CLIENT_NOT_FOUND 视为成功(目标状态本就是没了)。 */
    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable @NotBlank String id) {
        xrayInboundService.revoke(id);
        return Result.ok();
    }

    /** 轮换协议密钥；del→add→update DB 三步，中途失败会标 status=3 待 reconciler 修复。 */
    @PostMapping("/{id}/rotate")
    public Result<XrayInboundRespVO> rotate(@PathVariable @NotBlank String id) {
        return Result.ok(XrayInboundConvert.INSTANCE.convert(xrayInboundService.rotate(id)));
    }

    /** 实时流量与配额(从 backend 拉，不读 DB)。 */
    @GetMapping("/{id}/traffic")
    public Result<XrayInboundTrafficRespVO> traffic(@PathVariable @NotBlank String id) {
        XrayInbound e = xrayInboundService.findById(id);
        return Result.ok(XrayInboundConvert.INSTANCE.toTrafficVO(e, xrayInboundService.getTraffic(id)));
    }

    /** 把累计上下行计数清零；不影响 client 本身。 */
    @PostMapping("/{id}/reset-traffic")
    public Result<Void> resetTraffic(@PathVariable @NotBlank String id) {
        xrayInboundService.resetTraffic(id);
        return Result.ok();
    }
}
