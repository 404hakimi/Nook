package com.nook.biz.xray.service.impl;

import com.nook.biz.xray.api.XrayClientApi;
import com.nook.biz.xray.api.dto.ClientProvisionRequestDTO;
import com.nook.biz.xray.api.dto.ClientResultDTO;
import com.nook.biz.xray.api.dto.ClientTrafficDTO;
import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.controller.client.vo.XrayClientProvisionReqVO;
import com.nook.biz.xray.entity.XrayClient;
import com.nook.biz.xray.service.XrayClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class XrayClientApiImpl implements XrayClientApi {

    private final XrayClientService xrayClientService;

    @Override
    public ClientResultDTO provision(ClientProvisionRequestDTO req) {
        XrayClientProvisionReqVO reqVO = new XrayClientProvisionReqVO();
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
        XrayClient e = xrayClientService.provision(reqVO);
        return new ClientResultDTO(e.getId(), e.getClientUuid(), e.getClientEmail());
    }

    @Override
    public void revoke(String inboundEntityId) {
        xrayClientService.revoke(inboundEntityId);
    }

    @Override
    public ClientTrafficDTO getTraffic(String inboundEntityId) {
        XrayClientTraffic t = xrayClientService.getTraffic(inboundEntityId);
        return new ClientTrafficDTO(
                t.email(), t.upBytes(), t.downBytes(),
                t.totalBytes(), t.expiryEpochMillis(), t.enabled());
    }
}
