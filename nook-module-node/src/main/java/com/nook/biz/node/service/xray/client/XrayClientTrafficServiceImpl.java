package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.controller.xray.vo.XrayClientTrafficRespVO;
import com.nook.biz.node.convert.xray.XrayClientTrafficConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.client.XrayClientTrafficDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientTrafficMapper;
import com.nook.biz.node.framework.xray.cli.XrayStatsCli;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.biz.node.validator.XrayClientValidator;
import com.nook.biz.node.validator.XrayNodeValidator;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xray 客户端流量 Service 实现.
 *
 * <p>查询走 DB (定时采样把 xray 端当前累计值入库, DB 用"当前值 - 上次值"算增量累加);
 * 重置 = 清 DB 累计 + 把当前累计值设为新的基线, 后续采样自然从基线开始算.
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
    private XrayNodeValidator xrayNodeValidator;
    @Resource
    private XrayStatsCli statsCli;

    @Override
    public XrayClientTrafficRespVO getXrayClientTraffic(String id) {
        // 新鲜度 ≤ 一个采样周期 (默认 30min, 见 nook.traffic.sample-interval-ms)
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
        // 拿当前远端累计值当新基线, 跟 DB 累计清零原子提交; 后续采样自然从这个基线开始算增量.
        // 不清零远端计数器 (跟采样模型一致, 远端计数器单调递增, 不需要清).
        XrayClientDO client = clientValidator.validateExists(id);
        XrayNodeDO node = xrayNodeValidator.validateExists(client.getServerId());
        SshSession session = SshSessions.acquire(client.getServerId(), SshSessionScope.SHARED);
        XrayUserTrafficSnapshot snap = statsCli.readUserTraffic(
                session, node.getXrayApiPort(), client.getClientEmail(), false);

        xrayClientTrafficMapper.resetWithBaseline(
                java.util.UUID.randomUUID().toString().replace("-", ""),
                id, client.getServerId(),
                Math.max(0L, snap.getUpBytes()),
                Math.max(0L, snap.getDownBytes()),
                java.time.LocalDateTime.now());
    }
}
