package com.nook.biz.node.controller.resource;

import com.nook.biz.node.config.Socks5Properties;
import com.nook.biz.node.config.WebStreamingProperties;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksInstallReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestRespVO;
import com.nook.biz.node.service.resource.ResourceIpSocksService;
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
public class ResourceIpSocksController {

    @Resource
    private ResourceIpSocksService resourceIpSocksService;
    @Resource
    private StreamingEndpointSupport streamingSupport;
    @Resource
    private WebStreamingProperties webStreamingProperties;
    @Resource
    private Socks5Properties socks5Properties;

    @PostMapping(value = "/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installSocks5(@RequestBody @Valid ResourceIpSocksInstallReqVO reqVO) {
        // 缺 installTimeoutSeconds 走兜底, service validator 仍会拒空请求
        long secs = reqVO != null && reqVO.getInstallTimeoutSeconds() != null
                ? reqVO.getInstallTimeoutSeconds() : socks5Properties.getDefaultInstallTimeoutSeconds();
        Duration emitterTimeout = Duration.ofSeconds(secs).plus(webStreamingProperties.getEmitterBuffer());
        String streamKey = "socks5:" + (reqVO != null ? reqVO.getSshHost() : "unknown");
        return streamingSupport.stream(streamKey, emitterTimeout,
                lineSink -> resourceIpSocksService.installSocks5(reqVO, lineSink));
    }

    @PostMapping("/{ipId}/test")
    public Result<ResourceIpSocksTestRespVO> testSocks5(@PathVariable("ipId") String ipId,
                                                        @RequestBody @Valid ResourceIpSocksTestReqVO reqVO) {
        ResourceIpSocksTestRespVO result = resourceIpSocksService.testSocks5(ipId, reqVO);
        return Result.ok(result);
    }
}
