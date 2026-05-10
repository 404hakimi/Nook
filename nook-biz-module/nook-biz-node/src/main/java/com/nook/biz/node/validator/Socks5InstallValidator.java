package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.socks5.vo.Socks5InstallReqVO;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * SOCKS5 落地节点部署相关的业务校验.
 *
 * @author nook
 */
@Component
public class Socks5InstallValidator {

    /**
     * 部署入参完整校验: 字符串长度 + 端口范围 + 各档超时范围.
     *
     * @param reqVO 部署入参
     */
    public void validateForInstall(Socks5InstallReqVO reqVO) {
        if (ObjectUtil.isNull(reqVO)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "socks5 install 入参不能为空");
        }
        if (reqVO.getSshHost().length() > 128) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshHost 长度需 ≤ 128");
        }
        if (reqVO.getSshPort() < 1 || reqVO.getSshPort() > 65535) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshPort 范围 1-65535");
        }
        if (reqVO.getSshUser().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshUser 长度需 ≤ 64");
        }
        if (reqVO.getSshPassword().length() > 255) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshPassword 长度需 ≤ 255");
        }
        if (reqVO.getSshTimeoutSeconds() < 5 || reqVO.getSshTimeoutSeconds() > 600) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshTimeoutSeconds 范围 5-600");
        }
        if (reqVO.getSshOpTimeoutSeconds() < 5 || reqVO.getSshOpTimeoutSeconds() > 300) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshOpTimeoutSeconds 范围 5-300");
        }
        if (reqVO.getSshUploadTimeoutSeconds() < 5 || reqVO.getSshUploadTimeoutSeconds() > 600) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshUploadTimeoutSeconds 范围 5-600");
        }
        if (reqVO.getInstallTimeoutSeconds() < 60 || reqVO.getInstallTimeoutSeconds() > 3600) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "installTimeoutSeconds 范围 60-3600");
        }
        if (reqVO.getSocksPort() < 1 || reqVO.getSocksPort() > 65535) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "socksPort 范围 1-65535");
        }
        if (reqVO.getSocksUser().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "socksUser 长度需 ≤ 64");
        }
        if (reqVO.getSocksPass().length() > 255) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "socksPass 长度需 ≤ 255");
        }
        if (reqVO.getAllowFrom() != null && reqVO.getAllowFrom().length() > 255) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "allowFrom 长度需 ≤ 255");
        }
    }
}
