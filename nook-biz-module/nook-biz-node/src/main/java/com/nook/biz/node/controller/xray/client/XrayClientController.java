package com.nook.biz.node.controller.xray.client;

import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.convert.xray.client.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.service.xray.client.XrayClientService;
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

/** Xray 客户端后台管理; 运营调试用, 业务自动 provision 走 XrayClientApi. */
@RestController
@RequestMapping("/admin/node/xray/client")
@RequiredArgsConstructor
@Validated
public class XrayClientController {

    private final XrayClientService xrayClientService;

    /** 分页列 client 配置；过滤条件见 PageReqVO。 */
    @GetMapping
    public Result<PageResult<ClientRespVO>> page(@ModelAttribute ClientPageReqVO reqVO) {
        return Result.ok(XrayClientConvert.INSTANCE.convertPage(xrayClientService.page(reqVO)));
    }

    /** 详情；同样会被 mask UUID。 */
    @GetMapping("/{id}")
    public Result<ClientRespVO> detail(@PathVariable @NotBlank String id) {
        return Result.ok(XrayClientConvert.INSTANCE.convert(xrayClientService.findById(id)));
    }

    /** 手动开通；远端 backend.addClient 成功后才落 DB。重复 (memberId, ipId) 抛 CLIENT_DUPLICATE。 */
    @PostMapping("/provision")
    public Result<ClientRespVO> provision(@RequestBody @Valid ClientProvisionReqVO reqVO) {
        XrayClientDO e = xrayClientService.provision(reqVO);
        return Result.ok(XrayClientConvert.INSTANCE.convert(e));
    }

    /** 编辑本地元数据(listenIp/listenPort/transport/status)；不触达远端 backend。 */
    @PutMapping("/{id}")
    public Result<ClientRespVO> update(@PathVariable @NotBlank String id,
                                            @RequestBody @Valid ClientUpdateReqVO reqVO) {
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
    public Result<ClientRespVO> rotate(@PathVariable @NotBlank String id) {
        return Result.ok(XrayClientConvert.INSTANCE.convert(xrayClientService.rotate(id)));
    }

    /** 实时流量与配额(从 backend 拉，不读 DB)。 */
    @GetMapping("/{id}/traffic")
    public Result<ClientTrafficRespVO> traffic(@PathVariable @NotBlank String id) {
        return Result.ok(xrayClientService.getTraffic(id));
    }

    /** 把累计上下行计数清零；不影响 client 本身。 */
    @PostMapping("/{id}/reset-traffic")
    public Result<Void> resetTraffic(@PathVariable @NotBlank String id) {
        xrayClientService.resetTraffic(id);
        return Result.ok();
    }

    /** 协议级凭据明文 (UUID + host); 拼订阅链接用, 与 list/detail 的 mask 行为区分. */
    @GetMapping("/{id}/credential")
    public Result<ClientCredentialRespVO> credential(@PathVariable @NotBlank String id) {
        return Result.ok(xrayClientService.loadCredential(id));
    }
}
