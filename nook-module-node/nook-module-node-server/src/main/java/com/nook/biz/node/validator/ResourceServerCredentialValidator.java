package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 服务器 SSH 凭据业务校验
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class ResourceServerCredentialValidator {

    private final ResourceServerCredentialMapper credentialMapper;
    private final ResourceServerValidator serverValidator;

    public ResourceServerCredentialDO validateExists(String serverId) {
        ResourceServerCredentialDO row = credentialMapper.selectById(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NOT_FOUND, serverId);
        }
        return row;
    }

    public void validateHostUnique(String serverId, String host) {
        if (StrUtil.isBlank(host)) return;
        boolean dup = serverId == null
                ? credentialMapper.existsByHost(host)
                : credentialMapper.existsByHostExcludingId(host, serverId);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.SERVER_HOST_DUPLICATE, host);
        }
    }

    /**
     * 编辑校验: 主机唯一 + LIVE 后 host/port 硬锁.
     *
     * @param serverId       server 主键
     * @param current        当前 DB 凭据
     * @param reqVO          待保存的新值
     */
    public void validateUpdate(String serverId, ResourceServerCredentialDO current,
                               ResourceServerCredentialUpdateReqVO reqVO) {
        validateHostUnique(serverId, reqVO.getHost());
        ResourceServerDO srv = serverValidator.validateExists(serverId);
        if (!ResourceServerLifecycleEnum.LIVE.matches(srv.getLifecycleState())) return;
        boolean hostChanged = !StrUtil.equals(current.getHost(), reqVO.getHost());
        boolean portChanged = !ObjectUtil.equal(current.getSshPort(), reqVO.getSshPort());
        if (hostChanged || portChanged) {
            throw new BusinessException(ResourceErrorCode.SERVER_SSH_LOCKED_AFTER_LIVE);
        }
    }
}
