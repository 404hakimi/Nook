package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface LandingSocksOpsConvert {

    LandingSocksOpsConvert INSTANCE = Mappers.getMapper(LandingSocksOpsConvert.class);

    ServiceLogRespVO toServiceLogVO(JournalLogSnapshot snapshot);
}
