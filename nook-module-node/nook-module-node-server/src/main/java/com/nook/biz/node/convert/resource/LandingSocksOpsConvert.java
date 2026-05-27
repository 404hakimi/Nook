package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.HostInfoRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.Socks5StatusRespVO;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface LandingSocksOpsConvert {

    LandingSocksOpsConvert INSTANCE = Mappers.getMapper(LandingSocksOpsConvert.class);

    HostInfoRespVO toHostInfoVO(HostInfoSnapshot snapshot);

    ServiceLogRespVO toServiceLogVO(JournalLogSnapshot snapshot);

    @Mapping(target = "unit", source = "sysd.unit")
    @Mapping(target = "active", source = "sysd.active")
    @Mapping(target = "uptimeFrom", source = "sysd.uptimeFrom")
    @Mapping(target = "enabled", source = "sysd.enabled")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "listening", source = "listening")
    @Mapping(target = "ufwStatus", source = "ufw")
    @Mapping(target = "hostInfo", source = "host")
    Socks5StatusRespVO toSocks5StatusRespVO(SystemdStatusSnapshot sysd,
                                            String version,
                                            String listening,
                                            String ufw,
                                            HostInfoSnapshot host);
}
