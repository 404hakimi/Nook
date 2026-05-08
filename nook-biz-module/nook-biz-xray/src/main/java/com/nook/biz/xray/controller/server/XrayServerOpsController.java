package com.nook.biz.xray.controller.server;

import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;
import com.nook.biz.xray.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.xray.service.XrayInboundService;
import com.nook.biz.xray.util.SshExecutor;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.Result;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务器对接相关的运维操作接口；CRUD 在 {@code /admin/resource/servers/**}，
 * 这里仅集中"需要拨远端"的动作：探活、列远端 inbound、SSH 状态/重启等。
 */
@RestController
@RequestMapping("/admin/xray/servers")
@RequiredArgsConstructor
@Validated
public class XrayServerOpsController {

    private final XrayInboundService xrayInboundService;
    private final ResourceServerApi resourceServerApi;
    private final SshExecutor sshExecutor;

    /** 探活：调 backend.verifyConnectivity；成功返回耗时，失败返回 success=false + 错误描述。 */
    @PostMapping("/{id}/test")
    public Result<ConnectivityTestRespVO> testConnectivity(@PathVariable @NotBlank String id) {
        ConnectivityTestRespVO vo = new ConnectivityTestRespVO();
        try {
            ServerCredentialDTO cred = resourceServerApi.loadCredential(id);
            vo.setBackendType(cred.backendType());
            long elapsed = xrayInboundService.verifyConnectivity(id);
            vo.setSuccess(true);
            vo.setElapsedMs(elapsed);
        } catch (BusinessException be) {
            // 业务异常以友好结构返回，不再走 GlobalExceptionHandler
            vo.setSuccess(false);
            vo.setError(be.getMessage());
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
}
