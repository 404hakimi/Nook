package com.nook.biz.node.controller.xray;

import com.nook.biz.node.controller.xray.vo.XrayClientCredentialRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientPageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientRespVO;
import com.nook.biz.node.convert.xray.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.service.xray.client.XrayClientService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 管理后台 - Xray 客户端 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/xray/client")
@Validated
public class XrayClientController {

    @Resource
    private XrayClientService xrayClientService;

    /**
     * 获得 xray 客户端分页
     *
     * @param pageReqVO 分页条件
     * @return 客户端分页
     */
    @GetMapping("/page-xray-client")
    public Result<PageResult<XrayClientRespVO>> getXrayClientPage(@ModelAttribute XrayClientPageReqVO pageReqVO) {
        PageResult<XrayClientDO> pageResult = xrayClientService.getXrayClientPage(pageReqVO);
        XrayClientService.EnrichBundle bundle = xrayClientService.loadEnrichBundle(
                XrayClientConvert.collectServerIds(pageResult.getRecords()),
                XrayClientConvert.collectIpIds(pageResult.getRecords()));
        return Result.ok(XrayClientConvert.INSTANCE.convertPage(pageResult,
                bundle.ipMap(), bundle.serverMap(), bundle.hostMap(), bundle.configMap()));
    }

    /**
     * 获得 xray 客户端详情
     *
     * @param id 客户端编号
     * @return 客户端详情
     */
    @GetMapping("/get-xray-client")
    public Result<XrayClientRespVO> getXrayClient(@RequestParam("id") String id) {
        return Result.ok(convertOne(xrayClientService.getXrayClient(id)));
    }

    /**
     * 轮换客户端 UUID / 密钥
     *
     * @param id 客户端编号
     * @return 客户端详情
     */
    @PostMapping("/rotate-xray-client")
    public Result<XrayClientRespVO> rotateXrayClient(@RequestParam("id") String id) {
        return Result.ok(convertOne(xrayClientService.rotateXrayClient(id)));
    }

    /**
     * 拉协议级凭据明文
     *
     * @param id 客户端编号
     * @return 协议级凭据
     */
    @GetMapping("/get-xray-client-credential")
    public Result<XrayClientCredentialRespVO> getXrayClientCredential(@RequestParam("id") String id) {
        return Result.ok(xrayClientService.getXrayClientCredential(id));
    }

    /** 单条详情 / 轮换共用的回填路径. */
    private XrayClientRespVO convertOne(XrayClientDO entity) {
        List<XrayClientDO> single = List.of(entity);
        Set<String> serverIds = XrayClientConvert.collectServerIds(single);
        Set<String> ipIds = XrayClientConvert.collectIpIds(single);
        XrayClientService.EnrichBundle bundle = xrayClientService.loadEnrichBundle(serverIds, ipIds);
        return XrayClientConvert.INSTANCE.convert(entity,
                bundle.ipMap(), bundle.serverMap(), bundle.hostMap(), bundle.configMap());
    }
}
