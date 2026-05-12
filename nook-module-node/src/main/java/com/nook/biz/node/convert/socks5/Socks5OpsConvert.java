package com.nook.biz.node.convert.socks5;

import com.nook.biz.node.controller.socks5.vo.Socks5TestRespVO;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Socks5Prober snapshot ↔ controller VO.
 *
 * @author nook
 */
@Mapper
public interface Socks5OpsConvert {

    Socks5OpsConvert INSTANCE = Mappers.getMapper(Socks5OpsConvert.class);

    @Mapping(source = "errorMessage", target = "error")
    Socks5TestRespVO convert(Socks5ProbeSnapshot snapshot);
}
