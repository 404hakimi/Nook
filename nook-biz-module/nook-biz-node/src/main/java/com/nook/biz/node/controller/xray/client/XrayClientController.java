package com.nook.biz.node.controller.xray.client;

import jakarta.annotation.Resource;
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
 * Xray 客户端后台管理接口; 运营调试用, 业务自动 provision 走 XrayClientApi.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/xray/client")
@Validated
public class XrayClientController {

    @Resource
    private XrayClientService xrayClientService;

    /**
     * 分页列 client 配置, 出参 UUID 已 mask.
     *
     * @param reqVO 分页 + 过滤条件
     * @return PageResult of ClientRespVO
     */
    @GetMapping
    public Result<PageResult<ClientRespVO>> page(@ModelAttribute ClientPageReqVO reqVO) {
        return Result.ok(XrayClientConvert.INSTANCE.convertPage(xrayClientService.page(reqVO)));
    }

    /**
     * 单条 client 详情, 出参 UUID 已 mask.
     *
     * @param id xray_client.id
     * @return ClientRespVO
     */
    @GetMapping("/{id}")
    public Result<ClientRespVO> detail(@PathVariable @NotBlank String id) {
        return Result.ok(XrayClientConvert.INSTANCE.convert(xrayClientService.findById(id)));
    }

    /**
     * 手动开通 client, 远端 addUser 成功后才落 DB; 重复 (memberId, ipId) 抛 CLIENT_DUPLICATE.
     *
     * @param reqVO 开通入参
     * @return ClientRespVO
     */
    @PostMapping("/provision")
    public Result<ClientRespVO> provision(@RequestBody @Valid ClientProvisionReqVO reqVO) {
        XrayClientDO e = xrayClientService.provision(reqVO);
        return Result.ok(XrayClientConvert.INSTANCE.convert(e));
    }

    /**
     * 编辑本地元数据 (listenIp / listenPort / transport / status), 不触达远端.
     *
     * @param id    xray_client.id
     * @param reqVO 更新入参
     * @return ClientRespVO
     */
    @PutMapping("/{id}")
    public Result<ClientRespVO> update(@PathVariable @NotBlank String id,
                                            @RequestBody @Valid ClientUpdateReqVO reqVO) {
        return Result.ok(XrayClientConvert.INSTANCE.convert(xrayClientService.update(id, reqVO)));
    }

    /**
     * 吊销 client, 远端先删再软删 DB; 远端 CLIENT_NOT_FOUND 也算成功 (目标态本就是没了).
     *
     * @param id xray_client.id
     */
    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable @NotBlank String id) {
        xrayClientService.revoke(id);
        return Result.ok();
    }

    /**
     * 轮换协议密钥 (del 旧 → add 新 → 更新 DB), 中途失败标 status=3 待 reconciler 修复.
     *
     * @param id xray_client.id
     * @return ClientRespVO
     */
    @PostMapping("/{id}/rotate")
    public Result<ClientRespVO> rotate(@PathVariable @NotBlank String id) {
        return Result.ok(XrayClientConvert.INSTANCE.convert(xrayClientService.rotate(id)));
    }

    /**
     * 实时流量与配额, 从远端 stats 拉不读 DB.
     *
     * @param id xray_client.id
     * @return ClientTrafficRespVO
     */
    @GetMapping("/{id}/traffic")
    public Result<ClientTrafficRespVO> traffic(@PathVariable @NotBlank String id) {
        return Result.ok(xrayClientService.getTraffic(id));
    }

    /**
     * 累计上下行计数清零, 不影响 client 本身.
     *
     * @param id xray_client.id
     */
    @PostMapping("/{id}/reset-traffic")
    public Result<Void> resetTraffic(@PathVariable @NotBlank String id) {
        xrayClientService.resetTraffic(id);
        return Result.ok();
    }

    /**
     * 协议级凭据明文 (UUID + host), 拼订阅链接用; 与 list/detail 的 mask 行为区分.
     *
     * @param id xray_client.id
     * @return ClientCredentialRespVO
     */
    @GetMapping("/{id}/credential")
    public Result<ClientCredentialRespVO> credential(@PathVariable @NotBlank String id) {
        return Result.ok(xrayClientService.loadCredential(id));
    }
}
