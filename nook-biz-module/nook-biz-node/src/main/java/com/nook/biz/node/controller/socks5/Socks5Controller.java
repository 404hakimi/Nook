package com.nook.biz.node.controller.socks5;

import jakarta.annotation.Resource;
import com.nook.biz.node.controller.socks5.vo.Socks5InstallReqVO;
import com.nook.biz.node.controller.socks5.vo.Socks5TestRespVO;
import com.nook.framework.web.StreamingEndpointSupport;
import com.nook.biz.node.service.socks5.Socks5OpsService;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * SOCKS5 落地节点接口: 部署 + 拨号测试.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/socks5")
@Validated
public class Socks5Controller {

    /** install 流式整体超时; 部署脚本一般 1-3 分钟, 给 15 分钟兜底 apt 拉包慢的极端情况. */
    private static final Duration INSTALL_EMITTER_TIMEOUT = Duration.ofMinutes(15);

    @Resource
    private Socks5OpsService socks5OpsService;
    @Resource
    private StreamingEndpointSupport streamingSupport;

    /**
     * 流式部署 SOCKS5 落地, chunked transfer-encoding 远端 stdout 每行 flush 一行.
     *
     * @param reqVO 部署入参 (含 ad-hoc SSH 凭据 + SOCKS5 服务参数)
     * @return ResponseBodyEmitter
     */
    @PostMapping(value = "/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter install(@RequestBody @Valid Socks5InstallReqVO reqVO) {
        return streamingSupport.stream("socks5:" + reqVO.getSshHost(), INSTALL_EMITTER_TIMEOUT,
                lineSink -> socks5OpsService.installAdHocStreaming(reqVO, lineSink));
    }

    /**
     * 拨号测试已登记 IP 池条目对应的 SOCKS5 是否可用, 失败也返回结构化结果不抛 5xx.
     *
     * @param ipId resource_ip_pool.id
     * @return Socks5TestRespVO
     */
    @PostMapping("/{ipId}/test")
    public Result<Socks5TestRespVO> test(@PathVariable @NotBlank String ipId) {
        return Result.ok(socks5OpsService.testConnectivity(ipId));
    }
}
