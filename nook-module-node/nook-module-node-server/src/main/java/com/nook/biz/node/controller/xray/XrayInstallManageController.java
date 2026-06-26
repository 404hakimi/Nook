package com.nook.biz.node.controller.xray;

import com.nook.biz.node.api.enums.RealityDestPreset;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.xray.vo.ProtocolSchemaRespVO;
import com.nook.biz.node.controller.xray.vo.RealityDestSimpleRespVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallRespVO;
import com.nook.biz.node.service.xray.server.XrayInstallManageService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.Result;

import java.util.List;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 管理后台 - Xray 线路服务器运维 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/xray/install")
@Validated
public class XrayInstallManageController {

    @Resource
    private XrayInstallManageService xrayInstallManageService;

    /**
     * 获得 xray 实例详情
     *
     * @param serverId 服务器编号
     * @return xray 实例详情
     */
    @GetMapping("/get-xray-install")
    public Result<XrayInstallRespVO> getXrayInstall(@RequestParam("serverId") String serverId) {
        return Result.ok(xrayInstallManageService.getXrayInstallDetail(serverId));
    }

    /**
     * 重启 xray 服务
     *
     * @param id 服务器编号
     * @return systemd 输出
     */
    @PostMapping("/restart-xray")
    public Result<String> restartXray(@RequestParam("id") String id) {
        return Result.ok(xrayInstallManageService.restart(id));
    }

    /**
     * 获得 xray 日志文件内容
     *
     * @param id      服务器编号
     * @param variant 日志变体 (access / error)
     * @param lines   读取行数
     * @param keyword 关键字过滤
     * @return 日志内容
     */
    @GetMapping("/get-xray-log-file")
    public Result<ServiceLogRespVO> getXrayLogFile(@RequestParam("id") String id,
                                                   @RequestParam(value = "variant", required = false) String variant,
                                                   @RequestParam(value = "lines", required = false) Integer lines,
                                                   @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(xrayInstallManageService.getXrayLogFile(id, variant, lines, keyword));
    }

    /**
     * 装机 / 重装 xray (流式)
     *
     * @param id    服务器编号
     * @param reqVO 装机入参
     * @return 流式响应
     */
    @PostMapping(value = "/install-xray", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installXray(@RequestParam("id") String id,
                                           @Valid @RequestBody XrayInstallReqVO reqVO) {
        return xrayInstallManageService.installXrayStream(id, reqVO);
    }

    /**
     * 列 REALITY dest 候选 (装机前端 vless 协议时下拉)
     *
     * @return dest 候选列表
     */
    @GetMapping("/list-reality-dest")
    public Result<List<RealityDestSimpleRespVO>> listRealityDest() {
        List<RealityDestSimpleRespVO> list = CollectionUtils.convertList(
                List.of(RealityDestPreset.values()),
                p -> new RealityDestSimpleRespVO(p.getServerName(), p.getLabel() + " (" + p.getServerName() + ")"));
        return Result.ok(list);
    }

    /**
     * 列出全部入站协议的装机表单 schema (前端动态渲染协议下拉 + 字段, 加协议前端零改)
     *
     * @return 协议 schema 列表
     */
    @GetMapping("/list-protocols")
    public Result<List<ProtocolSchemaRespVO>> listProtocols() {
        return Result.ok(xrayInstallManageService.listProtocolSchemas());
    }
}
