package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.enums.XrayClientStatusEnum;
import com.nook.biz.node.api.xray.dto.XrayClientProvisionDTO;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.service.xray.client.XrayClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link XrayClientProvisionApi} 实现; 包装 {@link XrayClientService} (op 框架同步执行).
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class XrayClientProvisionApiImpl implements XrayClientProvisionApi {

    private final XrayClientService xrayClientService;
    private final XrayClientMapper xrayClientMapper;

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
        // 仅置停, 不删记录 / 不释放落地机; 远端由 reconcile 收敛 (RUNNING 过滤后自然移除)
        xrayClientMapper.updateStatus(clientId,
                XrayClientStatusEnum.STOPPED.getCode(), java.time.LocalDateTime.now());
    }

    @Override
    public void rebindFrontline(String clientId, String newServerId) {
        // 只改 server_id; 旧线路机 reconcile 摘除该 client, 新线路机 reconcile 装上 (uuid/落地不变)
        xrayClientMapper.updateServerId(clientId, newServerId);
    }

}
