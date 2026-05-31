package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.system.api.iptype.SystemIpTypeApi;
import com.nook.biz.system.api.iptype.dto.SystemIpTypeRespDTO;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SOCKS5 落地节点业务校验
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class ResourceServerLandingValidator {

    private final ResourceServerLandingMapper landingMapper;
    private final ResourceServerMapper resourceServerMapper;
    private final XrayClientMapper xrayClientMapper;
    private final SystemIpTypeApi systemIpTypeApi;

    /**
     * 校验落地节点存在
     *
     * @param serverId 落地节点编号
     * @return 落地节点子表 DO
     */
    public ResourceServerLandingDO validateExists(String serverId) {
        ResourceServerLandingDO e = landingMapper.selectByServerId(serverId);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(ResourceErrorCode.LANDING_NOT_FOUND, serverId);
        }
        return e;
    }

    /**
     * 校验 IP 类型存在
     *
     * @param ipTypeId system_ip_type.id
     */
    public void validateIpTypeExists(String ipTypeId) {
        SystemIpTypeRespDTO type = systemIpTypeApi.getById(ipTypeId);
        if (type == null) {
            throw new BusinessException(ResourceErrorCode.LANDING_NOT_FOUND, ipTypeId);
        }
    }

    /**
     * 校验 IP 地址全局唯一; id 用于排除自身 (Update), Create 传 null
     *
     * @param id        当前 server 编号 (Create 传 null)
     * @param ipAddress IP 地址
     */
    public void validateIpAddressUnique(String id, String ipAddress) {
        if (StrUtil.isBlank(ipAddress)) return;
        boolean dup = id == null
                ? resourceServerMapper.existsByIpAddress(ipAddress)
                : resourceServerMapper.existsByIpAddressExcludingId(ipAddress, id);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.LANDING_IP_DUPLICATE, ipAddress);
        }
    }

    /**
     * landing 创建聚合校验: ipTypeId 非空 + 存在; ipAddress 全局唯一
     *
     * @param ipTypeId  IP 类型 FK
     * @param ipAddress 出网 IP
     */
    public void validateForCreate(String ipTypeId, String ipAddress) {
        if (StrUtil.isBlank(ipTypeId)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "landing 必须提供 ipTypeId");
        }
        validateIpTypeExists(ipTypeId);
        validateIpAddressUnique(null, ipAddress);
    }

    /**
     * 校验 SSH 凭据齐 (用于装机 / SSH 运维前置)
     *
     * @param server server 主表 DO
     * @param cred   credential 子表 DO (可空)
     */
    public void validateSshCredentialReady(ResourceServerDO server, ResourceServerCredentialDO cred) {
        if (cred == null || StrUtil.isBlank(server.getIpAddress()) || StrUtil.isBlank(cred.getSshPassword())) {
            throw new BusinessException(ResourceErrorCode.LANDING_SSH_CRED_MISSING, server.getIpAddress());
        }
    }

    /**
     * 校验 SOCKS5 凭据齐 (用于装机前置: 端口 + 用户 + 密码 必须都已配置)
     *
     * @param landing landing 子表 DO
     */
    public void validateSocks5ConfigReady(ResourceServerLandingDO landing) {
        if (landing.getSocks5Port() == null
                || StrUtil.isBlank(landing.getSocks5Username())
                || StrUtil.isBlank(landing.getSocks5Password())) {
            throw new BusinessException(ResourceErrorCode.LANDING_SOCKS5_INCOMPLETE, landing.getServerId());
        }
    }

    /**
     * 删除守卫: 仍被 xray_client 绑定时拒绝
     *
     * @param serverId  落地节点编号
     * @param ipAddress 落地节点 IP (错误消息回显用)
     */
    public void validateNoBoundClient(String serverId, String ipAddress) {
        XrayClientDO bound = xrayClientMapper.selectByIpId(serverId);
        if (bound != null) {
            throw new BusinessException(ResourceErrorCode.LANDING_HAS_BOUND_CLIENT,
                    ipAddress, bound.getMemberUserId());
        }
    }

    /**
     * 是否仍有 xray_client 绑定该落地节点
     *
     * @param serverId 落地节点编号
     * @return 有客户端绑定返回 true
     */
    public boolean hasBoundClient(String serverId) {
        return xrayClientMapper.selectByIpId(serverId) != null;
    }
}
