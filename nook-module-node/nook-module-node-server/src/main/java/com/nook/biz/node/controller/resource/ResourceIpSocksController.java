package com.nook.biz.node.controller.resource;

import com.nook.biz.node.config.Socks5Properties;
import com.nook.framework.web.WebStreamingProperties;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksSyncCredsReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.Socks5StatusRespVO;
import com.nook.biz.node.service.resource.ResourceIpSocksService;
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

/**
 * 管理后台 - SOCKS5 落地节点 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/ip-pool")
@Validated
@RequiredArgsConstructor
public class ResourceIpSocksController {

    private final ResourceIpSocksService resourceIpSocksService;
    private final StreamingEndpointSupport streamingSupport;
    private final WebStreamingProperties webStreamingProperties;
    private final Socks5Properties socks5Properties;

    /**
     * 流式安装 SOCKS5 (dante); 针对已落库的 IP 池条目装机 + 状态切到 LIVE
     *
     * @param ipId 已存在的 IP 池编号
     * @return 流式响应
     */
    @PostMapping(value = "/install-socks5", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installSocks5(@RequestParam("ipId") String ipId) {
        long secs = socks5Properties.getDefaultInstallTimeoutSeconds();
        Duration emitterTimeout = Duration.ofSeconds(secs).plus(webStreamingProperties.getEmitterBuffer());
        String streamKey = "socks5:" + ipId;
        return streamingSupport.stream(streamKey, emitterTimeout,
                lineSink -> resourceIpSocksService.installSocks5(ipId, lineSink));
    }

    /**
     * 探活 SOCKS5 (走目标 IP, 验证 socks5 拨号 + 出网 IP)
     *
     * @param id    IP 池编号
     * @param reqVO 探活入参
     * @return 探活结果
     */
    @PostMapping("/test-socks5")
    public Result<ResourceIpSocksTestRespVO> testSocks5(@RequestParam("id") String id,
                                                        @Valid @RequestBody ResourceIpSocksTestReqVO reqVO) {
        ResourceIpSocksTestRespVO result = resourceIpSocksService.testSocks5(id, reqVO);
        return Result.ok(result);
    }

    /**
     * 流式同步 SOCKS5 凭据: landing dante config 热更新 + fra-line outbound 重建
     *
     * @param id    IP 池编号
     * @param reqVO 同步入参
     * @return 流式响应
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

    /**
     * 获得 SOCKS5 (dante) systemd 运行状态 + version / 监听端口
     *
     * @param id IP 池编号
     * @return SOCKS5 状态
     */
    @GetMapping("/socks5-status")
    public Result<Socks5StatusRespVO> getSocks5Status(@RequestParam("id") String id) {
        return Result.ok(resourceIpSocksService.getSocks5Status(id));
    }

    /**
     * 切 dante 开机自启 (systemctl enable/disable + DB.autostart_enabled 同步)
     *
     * @param id      IP 池编号
     * @param enabled 是否开机自启
     * @return 是否成功
     */
    @PostMapping("/socks5-autostart")
    public Result<Boolean> setSocks5Autostart(@RequestParam("id") String id,
                                              @RequestParam("enabled") boolean enabled) {
        resourceIpSocksService.setSocks5Autostart(id, enabled);
        return Result.ok(true);
    }

    /**
     * 获得 dante journal 日志 (journalctl -u danted)
     *
     * @param id      IP 池编号
     * @param lines   行数 (默认 100)
     * @param level   级别过滤
     * @param keyword 关键词过滤
     * @return 日志
     */
    @GetMapping("/socks5-log")
    public Result<ServiceLogRespVO> getSocks5Log(@RequestParam("id") String id,
                                                 @RequestParam(value = "lines", required = false) Integer lines,
                                                 @RequestParam(value = "level", required = false) String level,
                                                 @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(resourceIpSocksService.getSocks5Log(id, lines, level, keyword));
    }

    /**
     * 获得 dante 日志文件 (DB.log_path 指向); 跟 journal 互补
     *
     * @param id      IP 池编号
     * @param lines   行数
     * @param keyword 关键词过滤
     * @return 日志
     */
    @GetMapping("/socks5-log-file")
    public Result<ServiceLogRespVO> getSocks5LogFile(@RequestParam("id") String id,
                                                     @RequestParam(value = "lines", required = false) Integer lines,
                                                     @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(resourceIpSocksService.getSocks5LogFile(id, lines, keyword));
    }
}
