package com.nook.biz.xray.service.impl;

import com.nook.biz.xray.api.XrayInboundApi;
import com.nook.biz.xray.api.dto.InboundProvisionRequestDTO;
import com.nook.biz.xray.api.dto.InboundResultDTO;
import com.nook.biz.xray.api.dto.InboundTrafficDTO;
import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.controller.inbound.vo.XrayInboundProvisionReqVO;
import com.nook.biz.xray.entity.XrayInbound;
import com.nook.biz.xray.service.XrayInboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class XrayInboundApiImpl implements XrayInboundApi {

    private final XrayInboundService xrayInboundService;

    @Override
    public InboundResultDTO provision(InboundProvisionRequestDTO req) {
        XrayInboundProvisionReqVO reqVO = new XrayInboundProvisionReqVO();
        reqVO.setServerId(req.serverId());
        reqVO.setIpId(req.ipId());
        reqVO.setMemberUserId(req.memberUserId());
        reqVO.setExternalInboundRef(req.externalInboundRef());
        reqVO.setProtocol(req.protocol());
        reqVO.setTransport(req.transport());
        reqVO.setListenIp(req.listenIp());
        reqVO.setListenPort(req.listenPort());
        reqVO.setTotalBytes(req.totalBytes());
        reqVO.setExpiryEpochMillis(req.expiryEpochMillis());
        reqVO.setLimitIp(req.limitIp());
        reqVO.setFlow(req.flow());
        XrayInbound e = xrayInboundService.provision(reqVO);
        return new InboundResultDTO(e.getId(), e.getClientUuid(), e.getClientEmail());
    }

    @Override
    public void revoke(String inboundEntityId) {
        xrayInboundService.revoke(inboundEntityId);
    }

    @Override
    public InboundTrafficDTO getTraffic(String inboundEntityId) {
        XrayClientTraffic t = xrayInboundService.getTraffic(inboundEntityId);
        return new InboundTrafficDTO(
                t.email(), t.upBytes(), t.downBytes(),
                t.totalBytes(), t.expiryEpochMillis(), t.enabled());
    }
}
