package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
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

    private static final int MAX_LIMIT_IP = 100;

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

    /**
     * provision 入参业务校验; 字段级约束已由 jakarta validation 处理.
     *
     * @param reqVO provision 入参
     */
    public void validateForProvision(XrayClientProvisionReqVO reqVO) {
        validateExpiry(reqVO.getExpiryEpochMillis());
        validateLimitIp(reqVO.getLimitIp());
    }

    /**
     * 客户数硬上限校验: 该 server 活客户数 ≥ clientMaxCount 则不允许再开通.
     *
     * @param serverId       resource_server.id
     * @param clientMaxCount 该 server 的客户数上限 (来自 capacity.client_max_count); null/0 视为无上限不校验
     */
    public void validateClientMaxCount(String serverId, Integer clientMaxCount) {
        if (clientMaxCount == null || clientMaxCount == 0) return;
        long activeCount = xrayClientMapper.selectCount(Wrappers.<XrayClientDO>lambdaQuery()
                .eq(XrayClientDO::getServerId, serverId));
        if (activeCount >= clientMaxCount) {
            throw new BusinessException(XrayErrorCode.TOUCHDOWN_LIMIT_REACHED,
                    serverId, clientMaxCount);
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
