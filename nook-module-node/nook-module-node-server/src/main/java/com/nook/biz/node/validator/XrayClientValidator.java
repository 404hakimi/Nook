package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Xray 客户端业务校验.
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class XrayClientValidator {

    private final XrayClientMapper xrayClientMapper;

    /**
     * 校验客户端存在.
     *
     * @param id xray_client.id
     * @return XrayClientDO
     */
    public XrayClientDO validateExists(String id) {
        XrayClientDO e = xrayClientMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(XrayErrorCode.CLIENT_ENTITY_NOT_FOUND, id);
        }
        return e;
    }

    /**
     * 校验该 IP 当前未被任何 client 占用; 跟 xray_client.uk_ip_id UNIQUE 约束对齐
     *
     * @param ipId IP 池条目 id
     */
    public void validateIpNotInUse(String ipId) {
        XrayClientDO dup = xrayClientMapper.selectByIpId(ipId);
        if (ObjectUtil.isNotNull(dup)) {
            throw new BusinessException(XrayErrorCode.CLIENT_IP_ALREADY_USED, ipId);
        }
    }

}
