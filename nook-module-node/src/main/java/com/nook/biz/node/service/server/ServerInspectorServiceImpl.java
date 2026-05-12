package com.nook.biz.node.service.server;

import com.nook.biz.node.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.server.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.server.vo.SystemdStatusRespVO;
import com.nook.biz.node.convert.server.ServerInspectorConvert;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.snapshot.ConnectivitySnapshot;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.biz.node.service.support.SessionCredentialMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 服务器只读检视 Service 实现类
 *
 * @author nook
 */
@Service
public class ServerInspectorServiceImpl implements ServerInspectorService {

    @Resource
    private ServerProbe serverProbe;
    @Resource
    private SessionCredentialMapper sessionCredentialMapper;

    @Override
    public ConnectivityTestRespVO testConnectivity(String serverId) {
        // 跟"已连上之后 shell 通道工作正常"区分: acquire 阶段抛错时直接转结构化失败 (无凭据 / 网络断 / 鉴权失败).
        SshSession session;
        try {
            session = sessionCredentialMapper.acquire(serverId, SshSessionScope.SHARED);
        } catch (BusinessException be) {
            return ServerInspectorConvert.INSTANCE.convert(
                    new ConnectivitySnapshot(false, 0L, be.getMessage()));
        }
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.probeConnectivity(session));
    }

    @Override
    public ServerSystemInfoRespVO getSystemInfo(String serverId) {
        return ServerInspectorConvert.INSTANCE.convert(
                serverProbe.readHostInfo(sessionCredentialMapper.acquire(serverId, SshSessionScope.SHARED)));
    }

    @Override
    public SystemdStatusRespVO getSystemdStatus(String serverId, String unit) {
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readSystemdStatus(
                sessionCredentialMapper.acquire(serverId, SshSessionScope.SHARED), unit));
    }

    @Override
    public ServiceLogRespVO getServiceLog(String serverId, String unit, Integer logLines, String logLevel) {
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readJournalLog(
                sessionCredentialMapper.acquire(serverId, SshSessionScope.SHARED), unit, logLines, logLevel));
    }
}
