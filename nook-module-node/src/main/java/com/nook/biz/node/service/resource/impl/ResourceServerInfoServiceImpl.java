package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.controller.resource.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.SystemdStatusRespVO;
import com.nook.biz.node.convert.server.ServerInspectorConvert;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.snapshot.ConnectivitySnapshot;
import com.nook.biz.node.service.resource.ResourceServerInfoService;
import com.nook.framework.ssh.core.SshExecResult;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 服务器信息 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServerInfoServiceImpl implements ResourceServerInfoService {

    private final ServerProbe serverProbe;

    @Override
    public ConnectivityTestRespVO testConnectivity(String serverId) {
        // 跟"已连上之后 shell 通道工作正常"区分: acquire 阶段抛错时直接转结构化失败 (无凭据 / 网络断 / 鉴权失败).
        SshSession session;
        try {
            session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        } catch (BusinessException be) {
            return ServerInspectorConvert.INSTANCE.convert(
                    new ConnectivitySnapshot(false, 0L, be.getMessage()));
        }
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.probeConnectivity(session));
    }

    @Override
    public ServerSystemInfoRespVO getSystemInfo(String serverId) {
        return ServerInspectorConvert.INSTANCE.convert(
                serverProbe.readHostInfo(SshSessions.acquire(serverId, SshSessionScope.SHARED)));
    }

    @Override
    public SystemdStatusRespVO getSystemdStatus(String serverId, String unit) {
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readSystemdStatus(
                SshSessions.acquire(serverId, SshSessionScope.SHARED), unit));
    }

    @Override
    public ServiceLogRespVO getServiceLog(String serverId, String unit, Integer logLines, String logLevel, String keyword) {
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readJournalLog(
                SshSessions.acquire(serverId, SshSessionScope.SHARED), unit, logLines, logLevel, keyword));
    }

    @Override
    public List<String> listNetworkInterfaces(String serverId) {
        try {
            SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
            // -o 每行一个; 第二字段是 iface 名; 排除 loopback
            SshExecResult result = session.ssh().exec(
                    "ip -o link show | awk -F': ' '{print $2}' | grep -v '^lo$'",
                    Duration.ofSeconds(10));
            if (result.getExitCode() != 0) {
                log.warn("[listNetworkInterfaces] serverId={} exit={} stderr={}",
                        serverId, result.getExitCode(), result.getStderr());
                return List.of();
            }
            return Arrays.stream(result.getStdout().split("\\R"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (BusinessException be) {
            log.warn("[listNetworkInterfaces] serverId={} SSH 失败: {}", serverId, be.getMessage());
            return List.of();
        }
    }
}
