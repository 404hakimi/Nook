package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.xray.server.vo.LineServerInstallReqVO;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Xray 线路服务器部署相关的业务校验.
 *
 * @author nook
 */
@Component
public class XrayServerInstallValidator {

    /**
     * 部署入参完整校验: 字段范围 + 跨字段约束.
     *
     * @param reqVO 安装入参
     */
    public void validateForInstall(LineServerInstallReqVO reqVO) {
        if (ObjectUtil.isNull(reqVO)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "install 入参不能为空");
        }
        validateSlotPortBase(reqVO.getSlotPortBase());
        validateSlotPoolSize(reqVO.getSlotPoolSize());
        validateXrayApiPort(reqVO.getXrayApiPort());
        validateXrayVersion(reqVO.getXrayVersion());
        validateLogDir(reqVO.getLogDir());
        validateTimezone(reqVO.getTimezone());
        validateSlotPortNotConflictApiPort(reqVO);
    }

    /**
     * 校验 slot 端口段不能覆盖 xray api 端口, 否则启动时端口冲突.
     *
     * @param reqVO 安装入参
     */
    public void validateSlotPortNotConflictApiPort(LineServerInstallReqVO reqVO) {
        int slotEnd = reqVO.getSlotPortBase() + reqVO.getSlotPoolSize();
        if (reqVO.getXrayApiPort() >= reqVO.getSlotPortBase() && reqVO.getXrayApiPort() <= slotEnd) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "xrayApiPort 不能落在 slot 端口段 " + reqVO.getSlotPortBase() + "-" + slotEnd + " 内");
        }
    }

    private void validateSlotPortBase(Integer slotPortBase) {
        if (slotPortBase < 1024 || slotPortBase > 60000) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "slotPortBase 范围 1024-60000");
        }
    }

    private void validateSlotPoolSize(Integer slotPoolSize) {
        if (slotPoolSize < 1 || slotPoolSize > 200) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "slotPoolSize 范围 1-200");
        }
    }

    private void validateXrayApiPort(Integer xrayApiPort) {
        if (xrayApiPort < 1 || xrayApiPort > 65535) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "xrayApiPort 范围 1-65535");
        }
    }

    private void validateXrayVersion(String xrayVersion) {
        if (xrayVersion.length() > 32) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "xrayVersion 长度需 ≤ 32");
        }
    }

    private void validateLogDir(String logDir) {
        if (logDir.length() > 255) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "logDir 长度需 ≤ 255");
        }
    }

    private void validateTimezone(String timezone) {
        if (timezone.length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "timezone 长度需 ≤ 64");
        }
        // "skip" 表示不改远端时区, 任意 IANA tz 字符串前端兜底, 这里只校验长度
        if (StrUtil.isBlank(timezone)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "timezone 不能为空白 (skip 表示不改)");
        }
    }
}
