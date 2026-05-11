package com.nook.biz.node.controller.socks5;

import com.nook.biz.node.controller.socks5.vo.Socks5InstallReqVO;
import com.nook.biz.node.controller.socks5.vo.Socks5TestRespVO;
import com.nook.biz.node.service.socks5.Socks5OpsService;
import com.nook.common.web.response.Result;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;

/**
 * SOCKS5 落地节点接口; controller 仅做参数绑定 + 调 service, 校验由 service 内部完成.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/socks5")
public class Socks5Controller {

    /** Emitter 超时 = installTimeoutSeconds + buffer; ad-hoc 入参带 installTimeoutSeconds. */
    private static final Duration EMITTER_BUFFER = Duration.ofSeconds(60);

    @Resource
    private Socks5OpsService socks5OpsService;
    @Resource
    private StreamingEndpointSupport streamingSupport;

    @PostMapping(value = "/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter install(@RequestBody @Valid Socks5InstallReqVO reqVO) {
        // installTimeoutSeconds 用于设 Emitter 端超时; 字段缺失走 600s 兜底, service 内 validator 会抛 PARAM_INVALID
        long secs = reqVO != null && reqVO.getInstallTimeoutSeconds() != null
                ? reqVO.getInstallTimeoutSeconds() : 600L;
        Duration emitterTimeout = Duration.ofSeconds(secs).plus(EMITTER_BUFFER);
        String streamKey = "socks5:" + (reqVO != null ? reqVO.getSshHost() : "unknown");
        return streamingSupport.stream(streamKey, emitterTimeout,
                lineSink -> socks5OpsService.installAdHocStreaming(reqVO, lineSink));
    }

    @PostMapping("/{ipId}/test")
    public Result<Socks5TestRespVO> test(@PathVariable String ipId) {
        return Result.ok(socks5OpsService.testConnectivity(ipId));
    }
}
