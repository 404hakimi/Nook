package com.nook.biz.node.controller.xray;

import com.nook.biz.node.controller.xray.vo.XrayNodePageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayNodeRespVO;
import com.nook.biz.node.convert.xray.XrayNodeConvert;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.XrayNodeValidator;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 管理后台 - Xray 节点
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/xray/node")
@Validated
public class XrayNodeController {

    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private XrayNodeValidator xrayNodeValidator;
    @Resource
    private ResourceServerService resourceServerService;

    @GetMapping("/get")
    public Result<XrayNodeRespVO> getXrayNode(@RequestParam("serverId") String serverId) {
        XrayNodeDO entity = xrayNodeValidator.validateExists(serverId);
        XrayNodeRespVO vo = XrayNodeConvert.INSTANCE.convert(entity);
        Map<String, ResourceServerDO> serverMap = resourceServerService.getServerMap(Collections.singleton(serverId));
        ResourceServerDO s = serverMap.get(serverId);
        if (s != null) {
            vo.setServerName(s.getName());
            vo.setServerHost(s.getHost());
        }
        return Result.ok(vo);
    }

    @GetMapping("/page")
    public Result<PageResult<XrayNodeRespVO>> getXrayNodePage(@ModelAttribute XrayNodePageReqVO pageReqVO) {
        PageResult<XrayNodeDO> pageResult = xrayNodeService.getXrayNodePage(pageReqVO);
        Map<String, ResourceServerDO> serverMap = resourceServerService.getServerMap(
                XrayNodeConvert.collectServerIds(pageResult.getRecords()));
        return Result.ok(XrayNodeConvert.INSTANCE.convertPage(pageResult, serverMap));
    }
}
