package com.nook.biz.xray.controller.server;

import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;
import com.nook.biz.xray.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.xray.controller.server.vo.LineServerInstallReqVO;
import com.nook.biz.xray.service.ServerProvisioner;
import com.nook.biz.xray.service.XrayClientService;
import com.nook.biz.xray.service.XrayServiceStatus;
import com.nook.biz.xray.util.SshExecutor;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * 服务器对接相关的运维操作接口；CRUD 在 {@code /admin/resource/servers/**}，
 * 这里仅集中"需要拨远端"的动作：探活、列远端 inbound、SSH 状态/重启等。
 */
@Slf4j
@RestController
@RequestMapping("/admin/xray/servers")
@RequiredArgsConstructor
@Validated
public class XrayServerOpsController {

    private final XrayClientService xrayClientService;
    private final ResourceServerApi resourceServerApi;
    private final SshExecutor sshExecutor;
    private final ServerProvisioner serverProvisioner;
    /** Spring Boot 自带的 applicationTaskExecutor (ThreadPoolTaskExecutor); 项目里只有一个 AsyncTaskExecutor bean. */
    private final AsyncTaskExecutor asyncExecutor;

    /** 探活：调 backend.verifyConnectivity；任何异常都包成 success=false 返回，前端"测试连通性"按钮交互上必须有结构化结果。 */
    @PostMapping("/{id}/test")
    public Result<ConnectivityTestRespVO> testConnectivity(@PathVariable @NotBlank String id) {
        ConnectivityTestRespVO vo = new ConnectivityTestRespVO();
        long start = System.currentTimeMillis();
        log.info("[probe] start server={}", id);
        try {
            // 仅探测连通性, 不暴露内部 backend 细节
            long elapsed = xrayClientService.verifyConnectivity(id);
            vo.setSuccess(true);
            vo.setElapsedMs(elapsed);
            log.info("[probe] OK server={} elapsed={}ms", id, elapsed);
        } catch (BusinessException be) {
            vo.setSuccess(false);
            vo.setError(be.getMessage());
            // 业务异常已经是预期失败(凭据不全/远端拒绝/超时)，warn 级别足够；
            // 故意不打 stacktrace 因为 BusinessException 本就抑制了 fillInStackTrace
            log.warn("[probe] FAIL server={} code={} msg={} elapsed={}ms",
                    id, be.getCode(), be.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 非预期异常(NPE 之类)也要给前端友好响应——本接口语义就是"探活"，
            // 只要没探通都视为"失败"，不让用户看到 500
            vo.setSuccess(false);
            vo.setError("探活异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            // 非预期异常打全堆栈，方便定位
            log.error("[probe] UNEXPECTED server={} elapsed={}ms",
                    id, System.currentTimeMillis() - start, e);
        }
        return Result.ok(vo);
    }

    /** 列远端 inbound——给运营在 IP 关联界面里下拉用。 */
    @GetMapping("/{id}/inbounds")
    public Result<List<XrayInboundInfo>> remoteInbounds(@PathVariable @NotBlank String id) {
        return Result.ok(xrayClientService.listRemoteInbounds(id));
    }

    /** SSH: x-ui status。 */
    @GetMapping("/{id}/ssh/status")
    public Result<String> sshStatus(@PathVariable @NotBlank String id) {
        return Result.ok(sshExecutor.xuiStatus(resourceServerApi.loadCredential(id)));
    }

    /** SSH: 拉最近 N 行 x-ui 日志。 */
    @GetMapping("/{id}/ssh/log")
    public Result<String> sshLog(@PathVariable @NotBlank String id,
                                 @RequestParam(defaultValue = "100") int lines) {
        return Result.ok(sshExecutor.tailXuiLog(resourceServerApi.loadCredential(id), lines));
    }

    /** SSH: 重启 x-ui。 */
    @PostMapping("/{id}/ssh/restart")
    public Result<String> sshRestart(@PathVariable @NotBlank String id) {
        return Result.ok(sshExecutor.restartXui(resourceServerApi.loadCredential(id)));
    }

    /** SSH: 备份 SQLite 数据库到 /tmp/x-ui.db.bak。 */
    @PostMapping("/{id}/ssh/backup-db")
    public Result<String> sshBackupDb(@PathVariable @NotBlank String id) {
        return Result.ok(sshExecutor.backupXuiDb(resourceServerApi.loadCredential(id)));
    }

    // ===== Xray 服务运维 =====

    /**
     * Xray 服务结构化状态 + 系统基本信息 + 最近日志.
     * @param logLines 日志行数, 默认 30, 上限 5000
     * @param logLevel 日志级别: all(默认) / warning / err
     */
    @GetMapping("/{id}/xray/status")
    public Result<XrayServiceStatus> xrayStatus(@PathVariable @NotBlank String id,
                                                @RequestParam(required = false) Integer logLines,
                                                @RequestParam(required = false) String logLevel) {
        return Result.ok(serverProvisioner.xrayStatus(id, logLines, logLevel));
    }

    /** 重启 Xray 服务；客户连接会断 1-2 秒。 */
    @PostMapping("/{id}/xray/restart")
    public Result<String> xrayRestart(@PathVariable @NotBlank String id) {
        return Result.ok(serverProvisioner.restartXray(id));
    }

    /**
     * 一键安装/重装线路服务器(纯 Xray + nook 标配 config 含 grpc-api)。
     * <p><b>流式接口</b>: HTTP chunked transfer-encoding, Content-Type: text/plain;
     * 远端 stdout 每来一行就 flush 一行, 前端用 fetch + ReadableStream 逐行展示.
     * 耗时 1-5 分钟; 整体超时 15 分钟.
     */
    @PostMapping(value = "/{id}/xray/install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter xrayInstall(@PathVariable @NotBlank String id,
                                           @RequestBody @Valid LineServerInstallReqVO reqVO) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(15 * 60 * 1000L); // 15min

        // serverName 仅用于脚本头部注释; 直接 id 占位避免再开一次 DB 查询
        ServerProvisioner.LineServerInstallParams params = new ServerProvisioner.LineServerInstallParams(
                id,
                reqVO.getVmessPort(),
                reqVO.getXrayApiPort(),
                reqVO.getLogDir(),
                reqVO.getInstallUfw() != null && reqVO.getInstallUfw(),
                reqVO.getEnableBbr() != null && reqVO.getEnableBbr(),
                reqVO.getTimezone()
        );

        log.info("[provision-stream] start server={} params={}", id, params);
        long start = System.currentTimeMillis();

        // emitter.send 必须在另一个线程, 否则会阻塞 Spring MVC 主线程发请求(没法及时 flush)
        asyncExecutor.execute(() -> {
            try {
                serverProvisioner.installLineServerStreaming(id, params, line -> {
                    try {
                        emitter.send(line + "\n", MediaType.TEXT_PLAIN);
                    } catch (Exception sendErr) {
                        // 客户端断开 (浏览器关弹框 / 网络断) — 没法把 SSH 命令也 kill, 远端会跑完
                        // 这里仅停止 send, 不影响后端业务
                        log.warn("[provision-stream] client disconnected: {}", sendErr.getMessage());
                    }
                });
                emitter.complete();
                log.info("[provision-stream] OK server={} elapsed={}ms",
                        id, System.currentTimeMillis() - start);
            } catch (BusinessException be) {
                log.warn("[provision-stream] FAIL server={} code={} msg={} elapsed={}ms",
                        id, be.getCode(), be.getMessage(), System.currentTimeMillis() - start);
                trySendFinal(emitter, "[error] " + be.getMessage());
                emitter.completeWithError(be);
            } catch (Exception e) {
                log.error("[provision-stream] UNEXPECTED server={} elapsed={}ms",
                        id, System.currentTimeMillis() - start, e);
                trySendFinal(emitter, "[error] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** emitter 已经在出错的尾巴, 再尽力 send 一条错误信息给前端;失败就静默. */
    private void trySendFinal(ResponseBodyEmitter emitter, String message) {
        try {
            emitter.send(message + "\n", MediaType.TEXT_PLAIN);
        } catch (Exception ignored) {
            // 多半是 emitter 已经完成或客户端断开
        }
    }
}
