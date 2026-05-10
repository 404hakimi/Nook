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
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
 * Xray 客户端后台管理接口; controller 仅做参数绑定 + 调 service, 校验由 service 注入的 Validator 在内部完成.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/xray/client")
public class XrayClientController {

    @Resource
    private XrayClientService xrayClientService;

    @GetMapping
    public Result<PageResult<ClientRespVO>> page(@ModelAttribute ClientPageReqVO reqVO) {
        PageResult<ClientRespVO> page = XrayClientConvert.INSTANCE.convertPage(xrayClientService.page(reqVO));
        // page 出参一行一行展示给运维, 把 ipId hash 翻成可读 ipAddress; 这里走 service 的批量 enrich, 一次 SQL
        xrayClientService.enrichIpAddress(page.getRecords());
        return Result.ok(page);
    }

    @GetMapping("/{id}")
    public Result<ClientRespVO> detail(@PathVariable String id) {
        ClientRespVO vo = XrayClientConvert.INSTANCE.convert(xrayClientService.findById(id));
        xrayClientService.enrichIpAddress(java.util.Collections.singletonList(vo));
        return Result.ok(vo);
    }

    @PostMapping("/provision")
    public Result<ClientRespVO> provision(@RequestBody @Valid ClientProvisionReqVO reqVO) {
        XrayClientDO e = xrayClientService.provision(reqVO);
        ClientRespVO vo = XrayClientConvert.INSTANCE.convert(e);
        xrayClientService.enrichIpAddress(java.util.Collections.singletonList(vo));
        return Result.ok(vo);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id,
                               @RequestBody @Valid ClientUpdateReqVO reqVO) {
        xrayClientService.update(id, reqVO);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable String id) {
        xrayClientService.revoke(id);
        return Result.ok();
    }

    @PostMapping("/{id}/rotate")
    public Result<ClientRespVO> rotate(@PathVariable String id) {
        ClientRespVO vo = XrayClientConvert.INSTANCE.convert(xrayClientService.rotate(id));
        xrayClientService.enrichIpAddress(java.util.Collections.singletonList(vo));
        return Result.ok(vo);
    }

    @GetMapping("/{id}/traffic")
    public Result<ClientTrafficRespVO> traffic(@PathVariable String id) {
        return Result.ok(xrayClientService.getTraffic(id));
    }

    @PostMapping("/{id}/reset-traffic")
    public Result<Void> resetTraffic(@PathVariable String id) {
        xrayClientService.resetTraffic(id);
        return Result.ok();
    }

    @GetMapping("/{id}/credential")
    public Result<ClientCredentialRespVO> credential(@PathVariable String id) {
        return Result.ok(xrayClientService.loadCredential(id));
    }
}
