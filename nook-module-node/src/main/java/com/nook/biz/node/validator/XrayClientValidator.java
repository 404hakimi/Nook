package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
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

    private static final int MAX_LIMIT_IP = 100;

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

    /**
     * provision 入参业务校验; 字段级约束已由 jakarta validation 处理.
     *
     * @param reqVO provision 入参
     */
    public void validateForProvision(XrayClientProvisionReqVO reqVO) {
        validateFlow(reqVO.getFlow(), reqVO.getProtocol());
        validateExpiry(reqVO.getExpiryEpochMillis());
        validateLimitIp(reqVO.getLimitIp());
    }

    private void validateFlow(String flow, String protocol) {
        if (StrUtil.isBlank(flow)) return;
        if (!"vless".equalsIgnoreCase(protocol)) {
            throw new BusinessException(XrayErrorCode.CLIENT_PROVISION_INVALID,
                    "flow 仅 vless 协议支持, 当前 protocol=" + protocol);
        }
    }

    private void validateExpiry(Long expiryEpochMillis) {
        if (expiryEpochMillis == null || expiryEpochMillis == 0L) return;
        if (expiryEpochMillis <= System.currentTimeMillis()) {
            throw new BusinessException(XrayErrorCode.CLIENT_PROVISION_INVALID,
                    "expiryEpochMillis=" + expiryEpochMillis + " 不在未来");
        }
    }

    private void validateLimitIp(Integer limitIp) {
        if (limitIp == null) return;
        if (limitIp > MAX_LIMIT_IP) {
            throw new BusinessException(XrayErrorCode.CLIENT_PROVISION_INVALID,
                    "limitIp=" + limitIp + " 超过上限 " + MAX_LIMIT_IP);
        }
    }
}
