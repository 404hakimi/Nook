package com.nook.biz.node.controller.resource;

import com.nook.biz.node.config.Socks5Properties;
import com.nook.biz.node.config.WebStreamingProperties;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksInstallReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksSyncCredsReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.Socks5StatusRespVO;
import com.nook.biz.node.service.resource.ResourceIpSocksService;
import com.nook.common.web.response.Result;
import com.nook.framework.web.StreamingEndpointSupport;
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

import java.time.Duration;

/**
 * 管理后台 - SOCKS5 落地节点; 跟 ResourceIpPoolController 共用 /admin/resource/ip-pool 前缀
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/ip-pool")
@Validated
public class ResourceIpSocksController {

    @Resource
    private ResourceIpSocksService resourceIpSocksService;
    @Resource
    private StreamingEndpointSupport streamingSupport;
    @Resource
    private WebStreamingProperties webStreamingProperties;
    @Resource
    private Socks5Properties socks5Properties;

    @PostMapping(value = "/install-socks5", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installSocks5(@Valid @RequestBody ResourceIpSocksInstallReqVO reqVO) {
        // 缺 installTimeoutSeconds 走兜底, service validator 仍会拒空请求
        long secs = reqVO != null && reqVO.getInstallTimeoutSeconds() != null
                ? reqVO.getInstallTimeoutSeconds() : socks5Properties.getDefaultInstallTimeoutSeconds();
        Duration emitterTimeout = Duration.ofSeconds(secs).plus(webStreamingProperties.getEmitterBuffer());
        String streamKey = "socks5:" + (reqVO != null ? reqVO.getSshHost() : "unknown");
        return streamingSupport.stream(streamKey, emitterTimeout,
                lineSink -> resourceIpSocksService.installSocks5(reqVO, lineSink));
    }

    @PostMapping("/test-socks5")
    public Result<ResourceIpSocksTestRespVO> testSocks5(@RequestParam("id") String id,
                                                        @Valid @RequestBody ResourceIpSocksTestReqVO reqVO) {
        ResourceIpSocksTestRespVO result = resourceIpSocksService.testSocks5(id, reqVO);
        return Result.ok(result);
    }

    /**
     * 流式同步 SOCKS5 凭据: landing dante config 热更新 + fra-line outbound 重建.
     * 同 install-socks5 一样走 chunked transfer; 前端 fetch + ReadableStream 边读边显示.
     */
    @PostMapping(value = "/sync-creds", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter syncCreds(@RequestParam("id") String id,
                                         @Valid @RequestBody ResourceIpSocksSyncCredsReqVO reqVO) {
        long secs = reqVO != null && reqVO.getInstallTimeoutSeconds() != null
                ? reqVO.getInstallTimeoutSeconds() : 120L;
        Duration emitterTimeout = Duration.ofSeconds(secs).plus(webStreamingProperties.getEmitterBuffer());
        String streamKey = "socks5-sync:" + id;
        return streamingSupport.stream(streamKey, emitterTimeout,
                lineSink -> resourceIpSocksService.syncSocks5Creds(id, reqVO, lineSink));
    }

    /** SOCKS5 (dante) systemd 运行状态 + version / 监听端口; 用 IP 池条目存储的 SSH 凭据. */
    @GetMapping("/socks5-status")
    public Result<Socks5StatusRespVO> getSocks5Status(@RequestParam("id") String id) {
        return Result.ok(resourceIpSocksService.getSocks5Status(id));
    }

    /** 切 dante 开机自启 (systemctl enable/disable + DB.autostart_enabled 同步). */
    @PostMapping("/socks5-autostart")
    public Result<Boolean> setSocks5Autostart(@RequestParam("id") String id,
                                              @RequestParam("enabled") boolean enabled) {
        resourceIpSocksService.setSocks5Autostart(id, enabled);
        return Result.ok(true);
    }

    /** SOCKS5 落地节点 dante 日志 (journalctl -u danted), 跟 xray service-log 同语义. */
    @GetMapping("/socks5-log")
    public Result<ServiceLogRespVO> getSocks5Log(@RequestParam("id") String id,
                                                 @RequestParam(value = "lines", required = false) Integer lines,
                                                 @RequestParam(value = "level", required = false) String level,
                                                 @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(resourceIpSocksService.getSocks5Log(id, lines, level, keyword));
    }

    /**
     * SOCKS5 dante 自己的日志文件 (DB.log_path 指向); 跟 systemd journal 互补.
     * journal 看启动/失败, file 看真正的拨号记录, 前端 LogDialog 顶部切换.
     */
    @GetMapping("/socks5-log-file")
    public Result<ServiceLogRespVO> getSocks5LogFile(@RequestParam("id") String id,
                                                     @RequestParam(value = "lines", required = false) Integer lines,
                                                     @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(resourceIpSocksService.getSocks5LogFile(id, lines, keyword));
    }
}
