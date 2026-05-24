package com.nook.biz.node.controller.xray;

import com.nook.framework.web.WebStreamingProperties;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayServerRespVO;
import com.nook.biz.node.controller.xray.vo.XrayServerStatusRespVO;
import com.nook.biz.node.convert.xray.XrayServerConvert;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.server.XrayServerManageService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.XrayServerValidator;
import com.nook.common.web.response.Result;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - Xray 线路服务器运维 (server 上的 xray 实例 status / restart / autostart / install)
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/xray/server")
@Validated
@RequiredArgsConstructor
public class XrayServerManageController {

    private final XrayServerManageService xrayServerManageService;
    private final XrayServerValidator xrayServerValidator;
    private final ResourceServerService resourceServerService;
    private final StreamingEndpointSupport streamingSupport;
    private final ResourceServerValidator serverValidator;
    private final ResourceServerCredentialService credentialService;
    private final WebStreamingProperties webStreamingProperties;

    /**
     * 获得 xray 实例元数据 (server detail tab 用)
     *
     * @param serverId 服务器编号
     * @return xray 实例元数据
     */
    @GetMapping("/get")
    public Result<XrayServerRespVO> getXrayServer(@RequestParam("serverId") String serverId) {
        XrayServerDO entity = xrayServerValidator.validateExists(serverId);
        XrayServerRespVO vo = XrayServerConvert.INSTANCE.convert(entity);
        Set<String> ids = Collections.singleton(serverId);
        Map<String, ResourceServerDO> serverMap = resourceServerService.getServerMap(ids);
        Map<String, String> hostMap = credentialService.getHostMap(ids);
        XrayServerConvert.fillServer(vo, serverMap, hostMap);
        return Result.ok(vo);
    }

    /**
     * 获得 xray systemd 服务状态 (active / uptime / pid)
     *
     * @param id 服务器编号
     * @return xray 服务状态
     */
    @GetMapping("/status")
    public Result<XrayServerStatusRespVO> getXrayStatus(@RequestParam("id") String id) {
        XrayServerStatusRespVO status = xrayServerManageService.getXraySystemdStatus(id);
        return Result.ok(status);
    }

    /**
     * 重启 xray 服务
     *
     * @param id 服务器编号
     * @return systemd 输出
     */
    @PostMapping("/restart")
    public Result<String> restartXray(@RequestParam("id") String id) {
        String out = xrayServerManageService.restart(id);
        return Result.ok(out);
    }

    /**
     * 切换 xray 开机自启 (systemd enable / disable)
     *
     * @param id      服务器编号
     * @param enabled 是否开机自启
     * @return systemd 输出
     */
    @PostMapping("/autostart")
    public Result<String> setAutostart(@RequestParam("id") String id,
                                       @RequestParam("enabled") boolean enabled) {
        String out = xrayServerManageService.setAutostart(id, enabled);
        return Result.ok(out);
    }

    /**
     * 获得 xray 日志文件内容 (access / error 二选一; 跟 service-log/journalctl 互补)
     *
     * @param id      服务器编号
     * @param variant 日志变体 (access / error)
     * @param lines   读取行数
     * @param keyword 关键字过滤
     * @return 日志内容
     */
    @GetMapping("/log-file")
    public Result<ServiceLogRespVO> getXrayLogFile(@RequestParam("id") String id,
                                                   @RequestParam(value = "variant", required = false) String variant,
                                                   @RequestParam(value = "lines", required = false) Integer lines,
                                                   @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(xrayServerManageService.getXrayLogFile(id, variant, lines, keyword));
    }

    /**
     * 装机 / 重装 xray (流式; emitter 比 install timeout 略宽, 保证 Service 自身 timeout 时还能把错误吐回前端)
     *
     * @param id    服务器编号
     * @param reqVO 装机入参
     * @return 流式响应
     */
    @PostMapping(value = "/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installXray(@RequestParam("id") String id,
                                           @Valid @RequestBody XrayServerInstallReqVO reqVO) {
        serverValidator.validateExists(id);
        int installTimeout = credentialService.requireByServerId(id).getInstallTimeoutSeconds();
        Duration emitterTimeout = Duration.ofSeconds(installTimeout).plus(webStreamingProperties.getEmitterBuffer());
        return streamingSupport.stream("install:" + id, emitterTimeout,
                lineSink -> xrayServerManageService.installStreaming(id, reqVO, lineSink));
    }
}
