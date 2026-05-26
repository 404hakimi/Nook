package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
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
 * 服务器 SSH 凭据业务校验.
 *
 * <p>主机地址 = resource_server.ip_address (canonical), 不在凭据表; 这里只校验凭据本身字段
 * (port / user / password / timeouts). LIVE 后 ssh_port 硬锁 (防止断 agent 心跳).
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

    /**
     * 编辑校验: LIVE 后 ssh_port 硬锁 (host 改在 server 主表, 由 ResourceServerValidator 管).
     */
    public void validateUpdate(String serverId, ResourceServerCredentialDO current,
                               ResourceServerCredentialUpdateReqVO reqVO) {
        ResourceServerDO srv = serverValidator.validateExists(serverId);
        if (!ResourceServerLifecycleEnum.LIVE.matches(srv.getLifecycleState())) return;
        boolean portChanged = !ObjectUtil.equal(current.getSshPort(), reqVO.getSshPort());
        if (portChanged) {
            throw new BusinessException(ResourceErrorCode.SERVER_SSH_LOCKED_AFTER_LIVE);
        }
    }
}
