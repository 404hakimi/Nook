package com.nook.biz.xray.controller.server;

import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;
import com.nook.biz.xray.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.xray.controller.server.vo.LineServerInstallReqVO;
import com.nook.biz.xray.service.ServerProvisioner;
import com.nook.biz.xray.service.XrayInboundService;
import com.nook.biz.xray.service.XrayServiceStatus;
import com.nook.biz.xray.util.SshExecutor;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private final XrayInboundService xrayInboundService;
    private final ResourceServerApi resourceServerApi;
    private final SshExecutor sshExecutor;
    private final ServerProvisioner serverProvisioner;

    /** 探活：调 backend.verifyConnectivity；任何异常都包成 success=false 返回，前端"测试连通性"按钮交互上必须有结构化结果。 */
    @PostMapping("/{id}/test")
    public Result<ConnectivityTestRespVO> testConnectivity(@PathVariable @NotBlank String id) {
        ConnectivityTestRespVO vo = new ConnectivityTestRespVO();
        long start = System.currentTimeMillis();
        log.info("[probe] start server={}", id);
        try {
            // 仅探测连通性, 不暴露内部 backend 细节
            long elapsed = xrayInboundService.verifyConnectivity(id);
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
        return Result.ok(xrayInboundService.listRemoteInbounds(id));
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

    /** Xray 服务结构化状态(active/version/uptime/listening/最近 30 行日志)。 */
    @GetMapping("/{id}/xray/status")
    public Result<XrayServiceStatus> xrayStatus(@PathVariable @NotBlank String id) {
        return Result.ok(serverProvisioner.xrayStatus(id));
    }

    /** 重启 Xray 服务；客户连接会断 1-2 秒。 */
    @PostMapping("/{id}/xray/restart")
    public Result<String> xrayRestart(@PathVariable @NotBlank String id) {
        return Result.ok(serverProvisioner.restartXray(id));
    }

    /**
     * 一键安装/重装线路服务器 Xray + 标配 config(带 grpc-api)。
     * <p>耗时 1-5 分钟,前端 UI 应该提示"安装中,请耐心等待";HTTP 超时已在 SshExecutor 内放大到 600s.
     */
    @PostMapping("/{id}/xray/install")
    public Result<String> xrayInstall(@PathVariable @NotBlank String id,
                                      @RequestBody @Valid LineServerInstallReqVO reqVO) {
        // serverName 仅用于脚本头部注释,直接用 id 占位即可,不动 ResourceServerApi
        ServerProvisioner.LineServerInstallParams params = new ServerProvisioner.LineServerInstallParams(
                id,
                reqVO.getVmessPort(),
                reqVO.getXrayApiPort(),
                reqVO.getLogDir(),
                reqVO.getInstallUfw() != null && reqVO.getInstallUfw(),
                reqVO.getEnableBbr() != null && reqVO.getEnableBbr()
        );
        log.info("[provision] line server install start server={} params={}", id, params);
        String out = serverProvisioner.installLineServer(id, params);
        log.info("[provision] line server install OK server={} outputBytes={}", id, out.length());
        return Result.ok(out);
    }
}
