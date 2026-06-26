package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.landing.Socks5TestRespVO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.socks5.install.Socks5DeployRequest;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface LandingSocksOpsConvert {

    LandingSocksOpsConvert INSTANCE = Mappers.getMapper(LandingSocksOpsConvert.class);

    ServiceLogRespVO toServiceLogVO(JournalLogSnapshot snapshot);

    @Mapping(target = "error", source = "errorMessage")
    Socks5TestRespVO toSocks5TestVO(Socks5ProbeSnapshot snapshot);

    // socks5_install 期望态 → agent 装机请求; 0/1 标志转 boolean, sshPort/timeout 由 caller 入参
    default Socks5DeployRequest toDeployRequest(Socks5InstallDO l, int sshPort, int timeoutSeconds) {
        return Socks5DeployRequest.builder()
                .serverId(l.getServerId())
                .socks5Port(l.getSocks5Port())
                .socks5Username(l.getSocks5Username())
                .socks5Password(l.getSocks5Password())
                .logLevel(l.getLogLevel())
                .logPath(l.getLogPath())
                .installDir(l.getInstallDir())
                .confPath(l.getConfPath())
                .pamFile(l.getPamFile())
                .pwdFile(l.getPwdFile())
                .systemdUnit(l.getSystemdUnit())
                .firewallEnabled(isOn(l.getFirewallEnabled()))
                .logRotateEnabled(isOn(l.getLogRotateEnabled()))
                .sshPort(sshPort)
                .timeoutSeconds(timeoutSeconds)
                .build();
    }

    private static boolean isOn(Integer flag) {
        return Integer.valueOf(1).equals(flag);
    }
}
