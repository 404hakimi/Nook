package com.nook.biz.node.convert.server;

import com.nook.biz.node.controller.server.vo.ConnectivityTestRespVO;
import com.nook.biz.node.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.node.controller.server.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.server.vo.SystemdStatusRespVO;
import com.nook.biz.node.framework.server.snapshot.ConnectivitySnapshot;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** ServerProbe 的 framework snapshot ↔ controller VO 映射. */
@Mapper
public interface ServerInspectorConvert {

    ServerInspectorConvert INSTANCE = Mappers.getMapper(ServerInspectorConvert.class);

    @Mapping(source = "errorMessage", target = "error")
    ConnectivityTestRespVO convert(ConnectivitySnapshot snapshot);

    ServerSystemInfoRespVO convert(HostInfoSnapshot snapshot);

    SystemdStatusRespVO convert(SystemdStatusSnapshot snapshot);

    ServiceLogRespVO convert(JournalLogSnapshot snapshot);
}
