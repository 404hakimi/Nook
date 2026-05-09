package com.nook.biz.node.controller.xray.inbound;

import jakarta.annotation.Resource;
import com.nook.biz.node.controller.xray.inbound.vo.InboundSnapshotRespVO;
import com.nook.biz.node.service.xray.inbound.XrayInboundService;
import com.nook.common.web.response.Result;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Xray 入站查询接口, 给运营在 IP 关联界面下拉用.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/xray/inbound")
@Validated
public class XrayInboundController {

    @Resource
    private XrayInboundService xrayInboundService;

    /**
     * 列指定 server 的远端 inbound (跳过 nook 自管的 api 通道).
     *
     * @param serverId resource_server.id
     * @return List of InboundSnapshotRespVO
     */
    @GetMapping("/{serverId}/list")
    public Result<List<InboundSnapshotRespVO>> list(@PathVariable @NotBlank String serverId) {
        return Result.ok(xrayInboundService.listInbounds(serverId));
    }
}
