package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.enums.XrayErrorCode;
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
     * 校验同 (memberUserId, ipId) 当前没有 client 行; 吊销走硬删, 旧行不存在视为可重新 provision.
     *
     * @param memberUserId 会员 id
     * @param ipId         IP 池条目 id
     */
    public void validateNotDuplicate(String memberUserId, String ipId) {
        XrayClientDO dup = xrayClientMapper.selectByMemberAndIp(memberUserId, ipId);
        if (ObjectUtil.isNotNull(dup)) {
            throw new BusinessException(XrayErrorCode.CLIENT_DUPLICATE,
                    "memberUserId=" + memberUserId + " ipId=" + ipId);
        }
    }
}
