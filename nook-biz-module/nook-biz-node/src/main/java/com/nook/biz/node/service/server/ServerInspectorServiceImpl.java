package com.nook.biz.node.service.server;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.server.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.server.vo.SystemdStatusRespVO;
import com.nook.biz.node.convert.server.ServerInspectorConvert;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ServerInspectorServiceImpl implements ServerInspectorService {

    @Resource
    private ServerProbe serverProbe;

    @Override
    public ConnectivityTestRespVO testConnectivity(String serverId) {
        requireServerId(serverId);
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.probeConnectivity(serverId));
    }

    @Override
    public ServerSystemInfoRespVO getSystemInfo(String serverId) {
        requireServerId(serverId);
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readHostInfo(serverId));
    }

    @Override
    public SystemdStatusRespVO getSystemdStatus(String serverId, String unit) {
        requireServerId(serverId);
        if (StrUtil.isBlank(unit)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "unit 不能为空");
        }
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readSystemdStatus(serverId, unit));
    }

    @Override
    public ServiceLogRespVO getLog(String serverId, String unit, Integer logLines, String logLevel) {
        requireServerId(serverId);
        if (StrUtil.isBlank(unit)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "unit 不能为空");
        }
        return ServerInspectorConvert.INSTANCE.convert(serverProbe.readJournalLog(serverId, unit, logLines, logLevel));
    }

    private static void requireServerId(String serverId) {
        if (StrUtil.isBlank(serverId)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "serverId 不能为空");
        }
    }
}
