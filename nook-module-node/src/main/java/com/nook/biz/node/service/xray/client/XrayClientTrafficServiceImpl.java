package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.controller.xray.vo.XrayClientTrafficRespVO;
import com.nook.biz.node.convert.xray.XrayClientTrafficConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.client.XrayClientTrafficDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper;
import com.nook.biz.node.framework.xray.cli.XrayStatsCli;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.service.support.SessionCredentialMapper;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xray 客户端流量 Service 实现.
 *
 * <p>查询纯 DB (XrayTrafficSampleJob 定时把 xray in-memory 增量 upsert 到 xray_client_traffic);
 * 重置先清 DB 累计再调远端 statsquery --reset, 保证 DB 是权威源.
 *
 * @author nook
 */
@Slf4j
@Service
public class XrayClientTrafficServiceImpl implements XrayClientTrafficService {

    @Resource
    private XrayClientValidator clientValidator;
    @Resource
    private XrayClientTrafficMapper xrayClientTrafficMapper;
    @Resource
    private XrayNodeService xrayNodeService;
    @Resource
    private XrayStatsCli statsCli;
    @Resource
    private SessionCredentialMapper sessionCredentialMapper;

    @Override
    public XrayClientTrafficRespVO getXrayClientTraffic(String id) {
        // 纯 DB 查询: 流量由 XrayTrafficSampleJob 定时增量 upsert 到 xray_client_traffic;
        // 新鲜度 ≤ 一个 sample 周期 (默认 30min, 见 nook.traffic.sample-interval-ms).
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
    @Transactional(rollbackFor = Exception.class)
    public void resetXrayClientTraffic(String id) {
        // DB 是流量权威源 (累计行); 先清 DB, 失败回滚不动远端;
        // 仅清远端不清 DB 会导致下次查询 = dbAcc + 0, 用户看到"重置无效"
        XrayClientDO client = clientValidator.validateExists(id);
        xrayClientTrafficMapper.deleteByClientId(id);

        XrayNodeDO node = xrayNodeService.getXrayNode(client.getServerId());
        SshSession session = sessionCredentialMapper.acquire(client.getServerId(), SshSessionScope.SHARED);
        // reset=true 原子返回旧值并清零; DB 已删, 远端清零失败下一轮 sample 仍会对齐
        statsCli.readUserTraffic(session, node.getXrayApiPort(), client.getClientEmail(), true);
    }
}
