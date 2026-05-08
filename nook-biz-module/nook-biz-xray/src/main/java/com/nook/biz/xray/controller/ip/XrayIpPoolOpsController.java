package com.nook.biz.xray.controller.ip;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.ResourceIpPoolApi;
import com.nook.biz.resource.api.dto.IpPoolEntryDTO;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.controller.ip.vo.IpSocks5InstallReqVO;
import com.nook.biz.xray.service.ServerProvisioner;
import com.nook.common.web.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * IP 池(SOCKS5 落地节点) 一键运维接口。
 * IP 池条目本身不存 SSH 凭据(落地小 VPS 不在 resource_server 表), 凭据每次部署由前端临时填入。
 */
@Slf4j
@RestController
@RequestMapping("/admin/xray/ip-pool")
@RequiredArgsConstructor
@Validated
public class XrayIpPoolOpsController {

    private final ResourceIpPoolApi resourceIpPoolApi;
    private final ServerProvisioner serverProvisioner;
    private final AsyncTaskExecutor asyncExecutor;

    /**
     * 一键部署 SOCKS5 到指定 IP 池条目所在主机(流式日志).
     * <p>HTTP chunked transfer-encoding, Content-Type: text/plain;
     * 远端 stdout 每来一行就 flush 一行, 前端 fetch + ReadableStream 逐行展示.
     * 整体超时 15 分钟.
     * <p>部署成功后 socks5_host/port/user/password 会回写到 resource_ip_pool 表.
     */
    @PostMapping(value = "/{id}/install-socks5", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installSocks5(@PathVariable @NotBlank String id,
                                              @RequestBody @Valid IpSocks5InstallReqVO reqVO) {
        // 早期校验; SSH 鉴权(密码或私钥)必给一个
        if (StrUtil.isBlank(reqVO.getSshPassword()) && StrUtil.isBlank(reqVO.getSshPrivateKey())) {
            throw new BusinessException(com.nook.biz.xray.constant.XrayErrorCode.BACKEND_OPERATION_FAILED,
                    "<install-socks5>", "需提供 sshPassword 或 sshPrivateKey 之一");
        }
        // 探测 IP 条目存在; 不存在直接抛 BusinessException
        IpPoolEntryDTO entry = resourceIpPoolApi.loadEntry(id);

        ServerCredentialDTO cred = ServerCredentialDTO.builder()
                .serverId("ip-pool:" + id) // 仅供日志识别, 不参与 DB 查询
                .sshHost(reqVO.getSshHost())
                .sshPort(reqVO.getSshPort())
                .sshUser(reqVO.getSshUser())
                .sshPassword(StrUtil.blankToDefault(reqVO.getSshPassword(), null))
                .sshPrivateKey(StrUtil.blankToDefault(reqVO.getSshPrivateKey(), null))
                .sshTimeoutSeconds(reqVO.getSshTimeoutSeconds())
                .build();

        ServerProvisioner.Socks5LandingInstallParams params = new ServerProvisioner.Socks5LandingInstallParams(
                reqVO.getSocksPort(),
                reqVO.getSocksUser(),
                reqVO.getSocksPass(),
                reqVO.getAllowFrom(),
                reqVO.getInstallUfw() != null && reqVO.getInstallUfw()
        );

        ResponseBodyEmitter emitter = new ResponseBodyEmitter(15 * 60 * 1000L);
        log.info("[ip-deploy-stream] start ipId={} sshHost={} socksPort={}", id, reqVO.getSshHost(), reqVO.getSocksPort());
        long start = System.currentTimeMillis();

        asyncExecutor.execute(() -> {
            try {
                serverProvisioner.installSocks5LandingStreaming(cred, params, line -> {
                    try {
                        emitter.send(line + "\n", MediaType.TEXT_PLAIN);
                    } catch (Exception sendErr) {
                        log.warn("[ip-deploy-stream] client disconnected: {}", sendErr.getMessage());
                    }
                });
                // 成功后回写 socks5 信息(host 取请求里的 sshHost = IP 主机入口; 通常等于 ip_address)
                resourceIpPoolApi.updateSocks5(id,
                        StrUtil.blankToDefault(entry.getSocks5Host(), reqVO.getSshHost()),
                        reqVO.getSocksPort(),
                        reqVO.getSocksUser(),
                        reqVO.getSocksPass());
                trySend(emitter, "[nook] socks5_host/port/user/password 已回写 IP 池条目 " + id);
                emitter.complete();
                log.info("[ip-deploy-stream] OK ipId={} elapsed={}ms",
                        id, System.currentTimeMillis() - start);
            } catch (BusinessException be) {
                log.warn("[ip-deploy-stream] FAIL ipId={} code={} msg={} elapsed={}ms",
                        id, be.getCode(), be.getMessage(), System.currentTimeMillis() - start);
                trySend(emitter, "[error] " + be.getMessage());
                emitter.completeWithError(be);
            } catch (Exception e) {
                log.error("[ip-deploy-stream] UNEXPECTED ipId={} elapsed={}ms",
                        id, System.currentTimeMillis() - start, e);
                trySend(emitter, "[error] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void trySend(ResponseBodyEmitter emitter, String message) {
        try {
            emitter.send(message + "\n", MediaType.TEXT_PLAIN);
        } catch (Exception ignored) {
            // emitter 已完成或客户端已断开; 静默
        }
    }
}
