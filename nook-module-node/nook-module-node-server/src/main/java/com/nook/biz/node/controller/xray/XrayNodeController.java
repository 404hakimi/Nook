package com.nook.biz.node.controller.xray;

import com.nook.biz.node.controller.xray.vo.XrayNodePageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayNodeRespVO;
import com.nook.biz.node.convert.xray.XrayNodeConvert;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.XrayNodeValidator;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - Xray 节点
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/xray/node")
@Validated
@RequiredArgsConstructor
public class XrayNodeController {

    private final XrayNodeService xrayNodeService;
    private final XrayNodeValidator xrayNodeValidator;
    private final ResourceServerService resourceServerService;
    private final ResourceServerCredentialService credentialService;

    @GetMapping("/get")
    public Result<XrayNodeRespVO> getXrayNode(@RequestParam("serverId") String serverId) {
        XrayNodeDO entity = xrayNodeValidator.validateExists(serverId);
        XrayNodeRespVO vo = XrayNodeConvert.INSTANCE.convert(entity);
        Set<String> ids = Collections.singleton(serverId);
        Map<String, ResourceServerDO> serverMap = resourceServerService.getServerMap(ids);
        Map<String, String> hostMap = credentialService.getHostMap(ids);
        XrayNodeConvert.fillServer(vo, serverMap, hostMap);
        return Result.ok(vo);
    }

    @GetMapping("/page")
    public Result<PageResult<XrayNodeRespVO>> getXrayNodePage(@ModelAttribute XrayNodePageReqVO pageReqVO) {
        PageResult<XrayNodeDO> pageResult = xrayNodeService.getXrayNodePage(pageReqVO);
        Set<String> serverIds = XrayNodeConvert.collectServerIds(pageResult.getRecords());
        Map<String, ResourceServerDO> serverMap = resourceServerService.getServerMap(serverIds);
        Map<String, String> hostMap = credentialService.getHostMap(serverIds);
        return Result.ok(XrayNodeConvert.INSTANCE.convertPage(pageResult, serverMap, hostMap));
    }
}
