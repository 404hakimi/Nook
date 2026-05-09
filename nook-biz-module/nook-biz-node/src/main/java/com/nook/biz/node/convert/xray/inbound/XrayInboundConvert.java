package com.nook.biz.node.convert.xray.inbound;

import com.nook.biz.node.controller.xray.inbound.vo.InboundSnapshotRespVO;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** InboundSnapshot (SDK record) ↔ VO 转换; 字段名 tag → externalInboundRef 在此层完成语义改名. */
@Mapper
public interface XrayInboundConvert {

    XrayInboundConvert INSTANCE = Mappers.getMapper(XrayInboundConvert.class);

    @Mapping(source = "tag", target = "externalInboundRef")
    InboundSnapshotRespVO convert(InboundSnapshot snapshot);

    List<InboundSnapshotRespVO> convertList(List<InboundSnapshot> snapshots);
}
