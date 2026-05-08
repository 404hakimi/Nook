package com.nook.biz.xray.controller.provisioner;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.biz.xray.controller.provisioner.vo.Socks5InstallReqVO;
import com.nook.biz.xray.service.ServerProvisioner;
import com.nook.common.web.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * SOCKS5 落地节点独立部署接口; 不绑定 IP 池条目。
 * <p>典型流程: 运营在 IP 池页面点 "部署 SOCKS5" → 填 SSH + SOCKS5 参数 → 流式查看部署日志 →
 * 部署成功 → 前端把 SOCKS5 凭据 (host=出网 IP, port/user/pass) 自动预填到 "新增 IP" 表单一键落库。
 * SSH 凭据全程不入库, 用完即弃。
 */
@Slf4j
@RestController
@RequestMapping("/admin/xray/provisioner")
@RequiredArgsConstructor
@Validated
public class XrayProvisionerController {

    /** ResponseBodyEmitter 整体超时; 部署脚本一般 1-3 分钟, 给 15 分钟兜底 apt 拉包慢的极端情况。 */
    private static final long EMITTER_TIMEOUT_MS = 15 * 60 * 1000L;

    private final ServerProvisioner serverProvisioner;
    private final AsyncTaskExecutor asyncExecutor;

    /**
     * 流式部署 SOCKS5: chunked transfer-encoding + text/plain, 远端 stdout 每来一行就 flush 一行;
     * 前端 fetch + ReadableStream 逐行展示。
     */
    @PostMapping(value = "/socks5/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installSocks5(@RequestBody @Valid Socks5InstallReqVO reqVO) {
        // SSH 鉴权(密码或私钥)二选一 — 单字段 @NotBlank 表达不了, 这里手动校验
        if (StrUtil.isBlank(reqVO.getSshPassword()) && StrUtil.isBlank(reqVO.getSshPrivateKey())) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    "<install-socks5>", "需提供 sshPassword 或 sshPrivateKey 之一");
        }
        ServerCredentialDTO cred = ServerCredentialDTO.builder()
                .serverId("ad-hoc:" + reqVO.getSshHost()) // 仅供日志识别, 不参与 DB 查询
                .sshHost(reqVO.getSshHost())
                .sshPort(reqVO.getSshPort())
                .sshUser(reqVO.getSshUser())
                .sshPassword(reqVO.getSshPassword())
                .sshPrivateKey(reqVO.getSshPrivateKey())
                .sshTimeoutSeconds(reqVO.getSshTimeoutSeconds())
                .build();

        ServerProvisioner.Socks5LandingInstallParams params = new ServerProvisioner.Socks5LandingInstallParams(
                reqVO.getSocksPort(),
                reqVO.getSocksUser(),
                reqVO.getSocksPass(),
                reqVO.getAllowFrom(),
                reqVO.getInstallUfw()
        );

        ResponseBodyEmitter emitter = new ResponseBodyEmitter(EMITTER_TIMEOUT_MS);
        log.info("[provisioner] socks5 deploy start sshHost={} socksPort={}",
                reqVO.getSshHost(), reqVO.getSocksPort());
        long start = System.currentTimeMillis();

        asyncExecutor.execute(() -> {
            try {
                serverProvisioner.installSocks5LandingStreaming(cred, params, line -> {
                    try {
                        emitter.send(line + "\n", MediaType.TEXT_PLAIN);
                    } catch (Exception sendErr) {
                        // 客户端断开 (浏览器关弹框 / 网络断) — 远端会跑完, 这里仅停止 send
                        log.warn("[provisioner] client disconnected: {}", sendErr.getMessage());
                    }
                });
                emitter.complete();
                log.info("[provisioner] socks5 deploy OK sshHost={} elapsed={}ms",
                        reqVO.getSshHost(), System.currentTimeMillis() - start);
            } catch (BusinessException be) {
                log.warn("[provisioner] socks5 deploy FAIL sshHost={} code={} elapsed={}ms",
                        reqVO.getSshHost(), be.getCode(), System.currentTimeMillis() - start, be);
                trySend(emitter, "[error] " + be.getMessage());
                emitter.completeWithError(be);
            } catch (Exception e) {
                log.error("[provisioner] socks5 deploy UNEXPECTED sshHost={} elapsed={}ms",
                        reqVO.getSshHost(), System.currentTimeMillis() - start, e);
                trySend(emitter, "[error] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** emitter 已完成 / 客户端已断开时 send 会抛, 静默吞掉避免覆盖原始失败原因。 */
    private void trySend(ResponseBodyEmitter emitter, String message) {
        try {
            emitter.send(message + "\n", MediaType.TEXT_PLAIN);
        } catch (Exception ignored) {
        }
    }
}
