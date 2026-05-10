package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.common.web.error.CommonErrorCode;
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
     * 校验 protocol 仅限 vless / vmess / trojan.
     *
     * @param protocol 入参 protocol
     */
    public void validateProtocolSupported(String protocol) {
        if (!"vless".equals(protocol) && !"vmess".equals(protocol) && !"trojan".equals(protocol)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "protocol 必须是 vless / vmess / trojan 之一");
        }
    }

    /**
     * 校验同 (memberUserId, ipId) 没有未吊销的 client; 软删行已被 @TableLogic 自动跳过.
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

    /**
     * provision 入参完整校验 (协议白名单 + 数值非负 + 字符串长度 + 业务防重).
     *
     * @param reqVO provision 入参
     */
    public void validateForProvision(ClientProvisionReqVO reqVO) {
        if (ObjectUtil.isNull(reqVO)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "provision 入参不能为空");
        }
        validateProtocolSupported(reqVO.getProtocol());
        if (reqVO.getProtocol().length() > 16) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "protocol 长度需 ≤ 16");
        }
        if (reqVO.getFlow() != null && reqVO.getFlow().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "flow 长度需 ≤ 64");
        }
        if (reqVO.getTotalBytes() != null && reqVO.getTotalBytes() < 0) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "totalBytes 不能为负");
        }
        if (reqVO.getExpiryEpochMillis() != null && reqVO.getExpiryEpochMillis() < 0) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "expiryEpochMillis 不能为负");
        }
        if (reqVO.getLimitIp() != null && reqVO.getLimitIp() < 0) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "limitIp 不能为负");
        }
        validateNotDuplicate(reqVO.getMemberUserId(), reqVO.getIpId());
    }

    /**
     * update 入参字段范围校验 (字段全可空, 仅在传值时校验).
     *
     * @param reqVO update 入参
     */
    public void validateForUpdate(ClientUpdateReqVO reqVO) {
        if (ObjectUtil.isNull(reqVO)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "update 入参不能为空");
        }
        if (reqVO.getListenIp() != null && reqVO.getListenIp().length() > 45) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "listenIp 长度需 ≤ 45");
        }
        if (reqVO.getListenPort() != null
                && (reqVO.getListenPort() < 1 || reqVO.getListenPort() > 65535)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "listenPort 范围 1-65535");
        }
        if (reqVO.getTransport() != null && reqVO.getTransport().length() > 32) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "transport 长度需 ≤ 32");
        }
        if (reqVO.getStatus() != null && (reqVO.getStatus() < 1 || reqVO.getStatus() > 4)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "status 取值 1-4");
        }
    }
}
