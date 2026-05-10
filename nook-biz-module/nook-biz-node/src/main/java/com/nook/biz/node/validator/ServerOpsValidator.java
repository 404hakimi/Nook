package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.server.vo.EnableSwapReqVO;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 服务器 OS 调优 op 业务校验.
 *
 * @author nook
 */
@Component
public class ServerOpsValidator {

    /**
     * 校验 serverId 非空.
     *
     * @param serverId resource_server.id
     */
    public void validateServerId(String serverId) {
        if (StrUtil.isBlank(serverId)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "serverId 不能为空");
        }
    }

    /**
     * 校验启用 swap 入参: sizeMb 范围 256-8192.
     *
     * @param reqVO swap 入参
     */
    public void validateForEnableSwap(EnableSwapReqVO reqVO) {
        if (ObjectUtil.isNull(reqVO)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "swap 入参不能为空");
        }
        if (reqVO.getSizeMb() < 256 || reqVO.getSizeMb() > 8192) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sizeMb 范围 256-8192");
        }
    }
}
