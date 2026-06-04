package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Xray 客户端业务校验.
 *
 * @author nook
 */
@Component
public class XrayClientValidator {

    @Resource
    private XrayClientMapper xrayClientMapper;

    /**
     * 校验客户端存在
     *
     * @param id 客户端ID
     * @return XrayClientDO
     */
    public XrayClientDO validateExists(String id) {
        XrayClientDO client = xrayClientMapper.selectById(id);
        if (ObjectUtil.isNull(client)) {
            throw new BusinessException(XrayErrorCode.CLIENT_ENTITY_NOT_FOUND, id);
        }
        return client;
    }

    /**
     * 校验该 IP 当前未被任何客户端占用
     *
     * @param ipId IP 池条目ID
     */
    public void validateIpNotInUse(String ipId) {
        XrayClientDO dup = xrayClientMapper.selectByIpId(ipId);
        if (ObjectUtil.isNotNull(dup)) {
            throw new BusinessException(XrayErrorCode.CLIENT_IP_ALREADY_USED, ipId);
        }
    }

}
