package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Xray 节点业务校验.
 *
 * @author nook
 */
@Component
public class XrayNodeValidator {

    @Resource
    private XrayNodeMapper xrayNodeMapper;

    /**
     * 校验 xray 节点存在; 不存在抛 SERVER_STATE_NOT_FOUND (语义=该 server 还没通过 nook 部署 xray).
     *
     * @param serverId resource_server.id
     * @return XrayNodeDO
     */
    public XrayNodeDO validateExists(String serverId) {
        XrayNodeDO row = xrayNodeMapper.selectById(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(XrayErrorCode.SERVER_STATE_NOT_FOUND, serverId);
        }
        return row;
    }
}
