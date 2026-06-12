package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.landing.Socks5TestRespVO;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
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
}
