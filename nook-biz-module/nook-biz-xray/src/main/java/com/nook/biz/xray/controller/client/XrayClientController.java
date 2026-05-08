package com.nook.biz.xray.controller.client;

import com.nook.biz.xray.controller.client.vo.XrayClientCredentialRespVO;
import com.nook.biz.xray.controller.client.vo.XrayClientPageReqVO;
import com.nook.biz.xray.controller.client.vo.XrayClientProvisionReqVO;
import com.nook.biz.xray.controller.client.vo.XrayClientRespVO;
import com.nook.biz.xray.controller.client.vo.XrayClientTrafficRespVO;
import com.nook.biz.xray.controller.client.vo.XrayClientUpdateReqVO;
import com.nook.biz.xray.convert.XrayClientConvert;
import com.nook.biz.xray.entity.XrayClient;
import com.nook.biz.xray.service.XrayClientService;
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
 * 后台 Xray client 管理。
 * 业务流程触发的 provision 走 {@link com.nook.biz.xray.api.XrayClientApi} (订阅成功后调用);
 * 这里是运营/调试用的手动接口。
 */
@RestController
@RequestMapping("/admin/xray/clients")
@RequiredArgsConstructor
@Validated
public class XrayClientController {

    private final XrayClientService xrayClientService;

    /** 分页列 client 配置；过滤条件见 PageReqVO。 */
    @GetMapping
    public Result<PageResult<XrayClientRespVO>> page(@ModelAttribute XrayClientPageReqVO reqVO) {
        return Result.ok(XrayClientConvert.INSTANCE.convertPage(xrayClientService.page(reqVO)));
    }

    /** 详情；同样会被 mask UUID。 */
    @GetMapping("/{id}")
    public Result<XrayClientRespVO> detail(@PathVariable @NotBlank String id) {
        return Result.ok(XrayClientConvert.INSTANCE.convert(xrayClientService.findById(id)));
    }

    /** 手动开通；远端 backend.addClient 成功后才落 DB。重复 (memberId, ipId) 抛 CLIENT_DUPLICATE。 */
    @PostMapping("/provision")
    public Result<XrayClientRespVO> provision(@RequestBody @Valid XrayClientProvisionReqVO reqVO) {
        XrayClient e = xrayClientService.provision(reqVO);
        return Result.ok(XrayClientConvert.INSTANCE.convert(e));
    }

    /** 编辑本地元数据(listenIp/listenPort/transport/status)；不触达远端 backend。 */
    @PutMapping("/{id}")
    public Result<XrayClientRespVO> update(@PathVariable @NotBlank String id,
                                            @RequestBody @Valid XrayClientUpdateReqVO reqVO) {
        return Result.ok(XrayClientConvert.INSTANCE.convert(xrayClientService.update(id, reqVO)));
    }

    /** 吊销；远端先删 client 再软删 DB；远端 CLIENT_NOT_FOUND 视为成功(目标状态本就是没了)。 */
    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable @NotBlank String id) {
        xrayClientService.revoke(id);
        return Result.ok();
    }

    /** 轮换协议密钥；del→add→update DB 三步，中途失败会标 status=3 待 reconciler 修复。 */
    @PostMapping("/{id}/rotate")
    public Result<XrayClientRespVO> rotate(@PathVariable @NotBlank String id) {
        return Result.ok(XrayClientConvert.INSTANCE.convert(xrayClientService.rotate(id)));
    }

    /** 实时流量与配额(从 backend 拉，不读 DB)。 */
    @GetMapping("/{id}/traffic")
    public Result<XrayClientTrafficRespVO> traffic(@PathVariable @NotBlank String id) {
        XrayClient e = xrayClientService.findById(id);
        return Result.ok(XrayClientConvert.INSTANCE.toTrafficVO(e, xrayClientService.getTraffic(id)));
    }

    /** 把累计上下行计数清零；不影响 client 本身。 */
    @PostMapping("/{id}/reset-traffic")
    public Result<Void> resetTraffic(@PathVariable @NotBlank String id) {
        xrayClientService.resetTraffic(id);
        return Result.ok();
    }

    /**
     * 拉协议级凭据明文 (UUID + 服务器 host); 用于"分享给会员"等需要拼订阅链接的场景。
     * 与 list/detail 的 mask 行为区分: 这是专用的 reveal 通道, 调用方明确知晓敏感性。
     */
    @GetMapping("/{id}/credential")
    public Result<XrayClientCredentialRespVO> credential(@PathVariable @NotBlank String id) {
        return Result.ok(xrayClientService.loadCredential(id));
    }
}
