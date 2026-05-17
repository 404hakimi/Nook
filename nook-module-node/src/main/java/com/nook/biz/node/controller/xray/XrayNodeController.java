package com.nook.biz.node.controller.xray;

import com.nook.biz.node.controller.xray.vo.XrayNodePageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayNodeRespVO;
import com.nook.biz.node.controller.xray.vo.XrayTouchdownItemRespVO;
import com.nook.biz.node.convert.xray.XrayNodeConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.XrayNodeValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
public class XrayNodeController {

    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private XrayNodeValidator xrayNodeValidator;
    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private ResourceIpPoolService resourceIpPoolService;
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

    /**
     * 该 server 的"落地占用列表" = xray_client active 行 + 关联 ip_pool 拉 ip_address.
     *
     * <p>新模型下没有"出站池槽位"的概念了, 每个活客户就是一份落地占用; 容量上限是 xray_node.touchdownSize.
     */
    @GetMapping("/touchdown-list")
    public Result<List<XrayTouchdownItemRespVO>> getTouchdownList(@RequestParam("serverId") String serverId) {
        // 共享 inbound 模型下协议 / 传输是 server 级配置 (xray_node), 一次拉到 entrust convert 用
        XrayNodeDO node = xrayNodeValidator.validateExists(serverId);
        List<XrayClientDO> clients = xrayClientMapper.selectByServerId(serverId);
        Set<String> ipIds = CollectionUtils.convertSet(clients, XrayClientDO::getIpId);
        Map<String, String> ipAddrMap = resourceIpPoolService.getIpAddressMap(ipIds);
        List<XrayTouchdownItemRespVO> view = new ArrayList<>(clients.size());
        for (XrayClientDO c : clients) {
            XrayTouchdownItemRespVO item = new XrayTouchdownItemRespVO();
            item.setClientId(c.getId());
            item.setIpId(c.getIpId());
            item.setIpAddress(ipAddrMap.get(c.getIpId()));
            item.setClientEmail(c.getClientEmail());
            item.setProtocol(node.getProtocol());
            item.setTransport(node.getTransport());
            item.setClientStatus(c.getStatus());
            item.setCreatedAt(c.getCreatedAt());
            view.add(item);
        }
        return Result.ok(view);
    }
}
