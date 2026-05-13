package com.nook.biz.node.controller.xray;

import com.nook.biz.node.controller.xray.vo.XrayNodePageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayNodeRespVO;
import com.nook.biz.node.controller.xray.vo.XraySlotItemRespVO;
import com.nook.biz.node.convert.xray.XrayNodeConvert;
import com.nook.biz.node.convert.xray.XraySlotPoolConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.slot.XraySlotPoolDO;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.client.XrayClientService;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.service.xray.slot.XraySlotPoolService;
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
import java.util.List;
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
    private XraySlotPoolService xraySlotPoolService;
    @Resource
    private XrayClientService xrayClientService;
    @Resource
    private ResourceServerService resourceServerService;

    @GetMapping("/get")
    public Result<XrayNodeRespVO> getXrayNode(@RequestParam("serverId") String serverId) {
        XrayNodeDO entity = xrayNodeService.getXrayNode(serverId);
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
     * Slot 占用视图 = xray_node (slot_port_base) + xray_slot_pool (used 状态) + xray_client (客户字段) 三表派生
     *
     * <p>Controller 只负责拉数据 + 调 convert; 派生 / 拼装逻辑都在 {@link XraySlotPoolConvert}.
     */
    @GetMapping("/slot-list")
    public Result<List<XraySlotItemRespVO>> getSlotList(@RequestParam("serverId") String serverId) {
        // node 必须先存在 (无则抛 SERVER_STATE_NOT_FOUND); 拿 slot_port_base 派生 listen_port
        XrayNodeDO node = xrayNodeService.getXrayNode(serverId);
        int portBase = node.getSlotPortBase() == null ? 0 : node.getSlotPortBase();

        List<XraySlotPoolDO> slots = xraySlotPoolService.getSlotPoolList(serverId);
        Map<String, XrayClientDO> clientMap = xrayClientService.getXrayClientMap(
                XraySlotPoolConvert.collectUsedByIds(slots));

        return Result.ok(XraySlotPoolConvert.INSTANCE.convertSlotView(slots, portBase, clientMap));
    }
}
