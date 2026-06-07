package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.system.api.iptype.SystemIpTypeApi;
import com.nook.biz.trade.api.SubscriptionCertApi;
import com.nook.biz.system.api.iptype.dto.SystemIpTypeRespDTO;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SOCKS5 落地节点业务校验
 *
 * @author nook
 */
@Component
public class ResourceServerLandingValidator {

    @Resource
    private ResourceServerLandingMapper resourceServerLandingMapper;
    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private SubscriptionCertApi subscriptionCertApi;
    @Resource
    private SystemIpTypeApi systemIpTypeApi;

    /**
     * 校验落地节点存在
     *
     * @param serverId 落地节点ID
     * @return ResourceServerLandingDO
     */
    public ResourceServerLandingDO validateExists(String serverId) {
        ResourceServerLandingDO landing = resourceServerLandingMapper.selectByServerId(serverId);
        if (ObjectUtil.isNull(landing)) {
            throw new BusinessException(ResourceErrorCode.LANDING_NOT_FOUND, serverId);
        }
        return landing;
    }

    /**
     * 校验 IP 类型存在
     *
     * @param ipTypeId IP 类型ID
     */
    public void validateIpTypeExists(String ipTypeId) {
        SystemIpTypeRespDTO type = systemIpTypeApi.getById(ipTypeId);
        if (ObjectUtil.isNull(type)) {
            throw new BusinessException(ResourceErrorCode.LANDING_NOT_FOUND, ipTypeId);
        }
    }

    /**
     * 校验 IP 地址全局唯一 (排除自身)
     *
     * @param id        当前服务器ID (新增传 null 表示不排除自身)
     * @param ipAddress IP 地址
     */
    public void validateIpAddressUnique(String id, String ipAddress) {
        if (StrUtil.isBlank(ipAddress)) return;
        boolean dup = ObjectUtil.isNull(id)
                ? resourceServerMapper.existsByIpAddress(ipAddress)
                : resourceServerMapper.existsByIpAddressExcludingId(ipAddress, id);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.LANDING_IP_DUPLICATE, ipAddress);
        }
    }

    /**
     * 落地节点创建聚合校验: IP 类型必填且存在, IP 地址全局唯一
     *
     * @param ipTypeId  IP 类型ID
     * @param ipAddress 出网 IP
     */
    public void validateForCreate(String ipTypeId, String ipAddress) {
        if (StrUtil.isBlank(ipTypeId)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "落地节点必须提供 IP 类型");
        }
        this.validateIpTypeExists(ipTypeId);
        this.validateIpAddressUnique(null, ipAddress);
    }

    /**
     * 校验 SSH 凭据齐全
     *
     * @param server 服务器主表 DO
     * @param cred   凭据 DO (可空)
     */
    public void validateSshCredentialReady(ResourceServerDO server, ResourceServerCredentialDO cred) {
        if (ObjectUtil.isNull(cred) || StrUtil.isBlank(server.getIpAddress()) || StrUtil.isBlank(cred.getSshPassword())) {
            throw new BusinessException(ResourceErrorCode.LANDING_SSH_CRED_MISSING, server.getIpAddress());
        }
    }

    /**
     * 校验 SOCKS5 配置齐全 (端口 / 用户 / 密码)
     *
     * @param landing 落地节点 DO
     */
    public void validateSocks5ConfigReady(ResourceServerLandingDO landing) {
        if (ObjectUtil.isNull(landing.getSocks5Port())
                || StrUtil.isBlank(landing.getSocks5Username())
                || StrUtil.isBlank(landing.getSocks5Password())) {
            throw new BusinessException(ResourceErrorCode.LANDING_SOCKS5_INCOMPLETE, landing.getServerId());
        }
    }

    /**
     * 是否仍有客户端绑定该落地节点
     *
     * @param serverId 落地节点ID
     * @return 有客户端绑定返回 true
     */
    public boolean hasBoundClient(String serverId) {
        return ObjectUtil.isNotNull(subscriptionCertApi.getByIp(serverId));
    }
}
