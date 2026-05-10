package com.nook.biz.resource.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.constant.ResourceErrorCode;
import com.nook.biz.resource.controller.server.vo.ResourceServerSaveReqVO;
import com.nook.biz.resource.entity.ResourceServer;
import com.nook.biz.resource.mapper.ResourceServerMapper;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 服务器资源业务校验.
 *
 * @author nook
 */
@Component
public class ResourceServerValidator {

    @Resource
    private ResourceServerMapper resourceServerMapper;

    /**
     * 校验服务器存在.
     *
     * @param id resource_server.id
     * @return ResourceServer
     */
    public ResourceServer validateExists(String id) {
        ResourceServer e = resourceServerMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NOT_FOUND, id);
        }
        return e;
    }

    /**
     * 校验别名在全局唯一; id 传当前行 id 用于排除自身 (Update), Create 传 null.
     *
     * @param id   当前行 id (Create 传 null)
     * @param name 别名
     */
    public void validateNameUnique(String id, String name) {
        boolean dup = id == null
                ? resourceServerMapper.existsByName(name)
                : resourceServerMapper.existsByNameExcludingId(name, id);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.SERVER_NAME_DUPLICATE, name);
        }
    }

    /**
     * 校验主机 (host) 在全局唯一; id 用于排除自身.
     *
     * @param id   当前行 id (Create 传 null)
     * @param host 主机
     */
    public void validateHostUnique(String id, String host) {
        boolean dup = id == null
                ? resourceServerMapper.existsByHost(host)
                : resourceServerMapper.existsByHostExcludingId(host, id);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.SERVER_HOST_DUPLICATE, host);
        }
    }

    /**
     * Create 入参完整校验: 必填字段 + 字段范围 + 唯一性.
     *
     * @param reqVO save 入参
     */
    public void validateForCreate(ResourceServerSaveReqVO reqVO) {
        if (ObjectUtil.isNull(reqVO)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "server save 入参不能为空");
        }

        // 必填
        requireBlank(reqVO.getName(), "name");
        requireBlank(reqVO.getHost(), "host");
        requireNull(reqVO.getSshPort(), "sshPort");
        requireBlank(reqVO.getSshUser(), "sshUser");
        requireBlank(reqVO.getSshPassword(), "sshPassword");
        requireNull(reqVO.getSshTimeoutSeconds(), "sshTimeoutSeconds");
        requireNull(reqVO.getSshOpTimeoutSeconds(), "sshOpTimeoutSeconds");
        requireNull(reqVO.getSshUploadTimeoutSeconds(), "sshUploadTimeoutSeconds");
        requireNull(reqVO.getInstallTimeoutSeconds(), "installTimeoutSeconds");
        requireNull(reqVO.getTotalBandwidth(), "totalBandwidth");
        requireNull(reqVO.getStatus(), "status");

        validateFieldRanges(reqVO);
        validateNameUnique(null, reqVO.getName());
        validateHostUnique(null, reqVO.getHost());
    }

    /**
     * Update 入参字段范围校验; 字段全可空 (传啥校啥), 唯一性由 service 在判定字段变更后调 validateXxxUnique.
     *
     * @param reqVO save 入参
     */
    public void validateForUpdate(ResourceServerSaveReqVO reqVO) {
        if (ObjectUtil.isNull(reqVO)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "server save 入参不能为空");
        }
        validateFieldRanges(reqVO);
    }

    private void validateFieldRanges(ResourceServerSaveReqVO r) {
        if (r.getName() != null && r.getName().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "name 长度需 ≤ 64");
        }
        if (r.getHost() != null && r.getHost().length() > 128) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "host 长度需 ≤ 128");
        }
        if (r.getSshPort() != null && (r.getSshPort() < 1 || r.getSshPort() > 65535)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshPort 范围 1-65535");
        }
        if (r.getSshUser() != null && r.getSshUser().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshUser 长度需 ≤ 64");
        }
        if (r.getSshPassword() != null && r.getSshPassword().length() > 255) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshPassword 长度需 ≤ 255");
        }
        if (r.getSshTimeoutSeconds() != null
                && (r.getSshTimeoutSeconds() < 5 || r.getSshTimeoutSeconds() > 300)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshTimeoutSeconds 范围 5-300");
        }
        if (r.getSshOpTimeoutSeconds() != null
                && (r.getSshOpTimeoutSeconds() < 5 || r.getSshOpTimeoutSeconds() > 300)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshOpTimeoutSeconds 范围 5-300");
        }
        if (r.getSshUploadTimeoutSeconds() != null
                && (r.getSshUploadTimeoutSeconds() < 5 || r.getSshUploadTimeoutSeconds() > 600)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "sshUploadTimeoutSeconds 范围 5-600");
        }
        if (r.getInstallTimeoutSeconds() != null
                && (r.getInstallTimeoutSeconds() < 60 || r.getInstallTimeoutSeconds() > 3600)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "installTimeoutSeconds 范围 60-3600");
        }
        if (r.getMonthlyTrafficGb() != null
                && (r.getMonthlyTrafficGb() < 0 || r.getMonthlyTrafficGb() > 1_048_576)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "monthlyTrafficGb 范围 0-1048576");
        }
        if (r.getTotalBandwidth() != null && r.getTotalBandwidth() < 0) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "totalBandwidth 不能为负");
        }
        if (r.getIdcProvider() != null && r.getIdcProvider().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "idcProvider 长度需 ≤ 64");
        }
        if (r.getRegion() != null && r.getRegion().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "region 长度需 ≤ 64");
        }
        if (r.getStatus() != null && (r.getStatus() < 1 || r.getStatus() > 3)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "status 取值 1-3");
        }
        if (r.getRemark() != null && r.getRemark().length() > 512) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "remark 长度需 ≤ 512");
        }
    }

    private static void requireBlank(String value, String field) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, field + " 不能为空");
        }
    }

    private static void requireNull(Object value, String field) {
        if (ObjectUtil.isNull(value)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, field + " 不能为空");
        }
    }
}
