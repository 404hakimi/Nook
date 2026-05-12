package com.nook.biz.node.controller.socks5;

import com.nook.biz.node.controller.socks5.vo.Socks5InstallReqVO;
import com.nook.biz.node.controller.socks5.vo.Socks5TestRespVO;
import com.nook.biz.node.service.socks5.Socks5OpsService;
import com.nook.common.web.response.Result;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;

/**
 * 管理后台 - SOCKS5 落地节点
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/socks5")
@Validated
public class Socks5Controller {

    /** Emitter 端比 install 端多留的余量, 给 service 自身超时收尾的窗口. */
    private static final Duration EMITTER_BUFFER = Duration.ofSeconds(60);

    /** install 超时缺省值 (秒); reqVO.installTimeoutSeconds 为空时兜底. */
    private static final long DEFAULT_INSTALL_TIMEOUT_SECONDS = 600L;

    @Resource
    private Socks5OpsService socks5OpsService;
    @Resource
    private StreamingEndpointSupport streamingSupport;

    @PostMapping(value = "/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installSocks5(@RequestBody @Valid Socks5InstallReqVO reqVO) {
        // Emitter 端比 install 端长一点, 留 service 主动报错的窗口; 缺 installTimeoutSeconds 走兜底, service validator 仍会拒
        long secs = reqVO != null && reqVO.getInstallTimeoutSeconds() != null
                ? reqVO.getInstallTimeoutSeconds() : DEFAULT_INSTALL_TIMEOUT_SECONDS;
        Duration emitterTimeout = Duration.ofSeconds(secs).plus(EMITTER_BUFFER);
        String streamKey = "socks5:" + (reqVO != null ? reqVO.getSshHost() : "unknown");
        return streamingSupport.stream(streamKey, emitterTimeout,
                lineSink -> socks5OpsService.installSocks5(reqVO, lineSink));
    }

    @PostMapping("/{ipId}/test")
    public Result<Socks5TestRespVO> testSocks5(@PathVariable("ipId") String ipId) {
        Socks5TestRespVO result = socks5OpsService.testSocks5(ipId);
        return Result.ok(result);
    }
}
