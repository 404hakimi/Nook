package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.entity.ResourceServerCredentialDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.mapper.ResourceServerCredentialMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 服务器 SSH 凭据业务校验.
 *
 * <p>仅校验凭据本身 (端口 / 账号 / 密码 / 超时); 主机地址在服务器主表. 运行中后 SSH 端口锁定, 防止断开 agent 心跳.
 *
 * @author nook
 */
@Component
public class ResourceServerCredentialValidator {

    @Resource
    private ResourceServerCredentialMapper resourceServerCredentialMapper;
    @Resource
    private ResourceServerValidator resourceServerValidator;

    /**
     * 校验凭据存在并返回
     *
     * @param serverId 服务器ID
     * @return ResourceServerCredentialDO
     */
    public ResourceServerCredentialDO validateExists(String serverId) {
        ResourceServerCredentialDO row = resourceServerCredentialMapper.selectById(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NOT_FOUND, serverId);
        }
        return row;
    }

    /**
     * 校验凭据更新: 服务器运行中后锁定 SSH 端口
     *
     * @param serverId 服务器ID
     * @param current  当前凭据
     * @param reqVO    更新入参
     */
    public void validateUpdate(String serverId, ResourceServerCredentialDO current,
                               ResourceServerCredentialUpdateReqVO reqVO) {
        ResourceServerDO srv = resourceServerValidator.validateExists(serverId);
        if (!ResourceServerLifecycleEnum.LIVE.matches(srv.getLifecycleState())) return;
        boolean portChanged = !ObjectUtil.equal(current.getSshPort(), reqVO.getSshPort());
        if (portChanged) {
            throw new BusinessException(ResourceErrorCode.SERVER_SSH_LOCKED_AFTER_LIVE);
        }
    }
}
