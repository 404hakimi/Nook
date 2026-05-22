package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.controller.xray.vo.XrayClientTrafficRespVO;
import com.nook.biz.node.convert.xray.XrayClientTrafficConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.client.XrayClientTrafficDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.dto.OpEnqueueRequest;
import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.api.spi.OpOrchestrator;
import com.nook.framework.security.stp.StpSystemUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Xray 客户端流量 Service 实现.
 *
 * <p>查询走 DB (定时采样把 xray 端当前累计值入库, DB 用"当前值 - 上次值"算增量累加);
 * 重置走 OpOrchestrator 入队, 与 rotate/sync/revoke 串行互斥, 同时拿到 op_log 审计 + DUPLICATE_OP 去重.
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XrayClientTrafficServiceImpl implements XrayClientTrafficService {

    private final XrayClientValidator clientValidator;
    private final XrayClientTrafficMapper xrayClientTrafficMapper;
    private final OpOrchestrator opOrchestrator;
    private final OpConfigResolver opConfigResolver;

    @Override
    public XrayClientTrafficRespVO getXrayClientTraffic(String id) {
        // 流量来源: agent 每 5min push xray statsquery → xray_client_traffic 表; 这里读最新累计
        XrayClientDO client = clientValidator.validateExists(id);
        XrayClientTrafficDO row = xrayClientTrafficMapper.selectByClientId(id);
        long dbUp = row == null || row.getUplinkBytes() == null ? 0L : row.getUplinkBytes();
        long dbDown = row == null || row.getDownlinkBytes() == null ? 0L : row.getDownlinkBytes();

        XrayUserTrafficSnapshot snap = XrayUserTrafficSnapshot.builder()
                .email(client.getClientEmail())
                .upBytes(dbUp)
                .downBytes(dbDown)
                .enabled(true)
                .build();
        return XrayClientTrafficConvert.INSTANCE.toTrafficVO(client, snap);
    }

    @Override
    public void resetXrayClientTraffic(String id) {
        XrayClientDO client = clientValidator.validateExists(id);
        OpEnqueueRequest req = OpEnqueueRequest.builder()
                .serverId(client.getServerId())
                .opType(OpType.CLIENT_RESET_TRAFFIC.name())
                .targetId(id)
                .operator(StpSystemUtil.getLoginIdOrSystem())
                .paramsJson("{\"clientId\":\"" + id + "\"}")
                .build();
        opOrchestrator.submitAndWait(req, opConfigResolver.getWaitTimeout(OpType.CLIENT_RESET_TRAFFIC.name()), Void.class);
    }
}
