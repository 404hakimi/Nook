package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.enums.XrayClientStatusEnum;
import com.nook.biz.node.api.xray.dto.XrayClientProvisionDTO;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.service.xray.client.XrayClientService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * {@link XrayClientProvisionApi} 实现; 全部仅写库 (改库即期望态), 远端 xray 由 agent 对账同步, 不走操作队列.
 *
 * @author nook
 */
@Service
public class XrayClientProvisionApiImpl implements XrayClientProvisionApi {

    @Resource
    private XrayClientService xrayClientService;
    @Resource
    private XrayClientMapper xrayClientMapper;

    @Override
    public String provision(XrayClientProvisionDTO req) {
        XrayClientProvisionReqVO reqVO = new XrayClientProvisionReqVO();
        reqVO.setServerId(req.getServerId());
        reqVO.setIpId(req.getIpId());
        reqVO.setMemberUserId(req.getMemberUserId());
        XrayClientDO client = xrayClientService.provisionXrayClient(reqVO);
        return client.getId();
    }

    @Override
    public void revoke(String clientId) {
        xrayClientService.revokeXrayClient(clientId);
    }

    @Override
    public void stop(String clientId) {
        // 仅置停, 不删记录 / 不释放落地机; 远端由对账同步 (运行态过滤后自然移除)
        xrayClientMapper.updateStatus(clientId,
                XrayClientStatusEnum.STOPPED.getCode(), LocalDateTime.now());
    }

    @Override
    public void resume(String clientId) {
        // 置回运行态; 落地机未释放, 远端由对账同步自动装回
        xrayClientMapper.updateStatus(clientId,
                XrayClientStatusEnum.RUNNING.getCode(), LocalDateTime.now());
    }

    @Override
    public void rebindFrontline(String clientId, String newServerId) {
        // 只改 server_id; 旧线路机对账时摘除该客户端, 新线路机对账时装上 (uuid/落地不变)
        xrayClientMapper.updateServerId(clientId, newServerId);
    }
}
