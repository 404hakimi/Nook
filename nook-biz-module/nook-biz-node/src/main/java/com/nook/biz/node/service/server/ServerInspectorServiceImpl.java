package com.nook.biz.node.service.server;

import com.nook.biz.node.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.server.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.server.vo.SystemdStatusRespVO;
import com.nook.biz.node.convert.server.ServerInspectorConvert;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServerInspectorServiceImpl implements ServerInspectorService {

    private final ServerProbe serverProbe;

    @Override
    public ConnectivityTestRespVO testConnectivity(String serverId) {
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.probeConnectivity(serverId));
    }

    @Override
    public ServerSystemInfoRespVO getSystemInfo(String serverId) {
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readHostInfo(serverId));
    }

    @Override
    public SystemdStatusRespVO getSystemdStatus(String serverId, String unit) {
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readSystemdStatus(serverId, unit));
    }

    @Override
    public ServiceLogRespVO getLog(String serverId, String unit, Integer logLines, String logLevel) {
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readJournalLog(serverId, unit, logLines, logLevel));
    }
}
