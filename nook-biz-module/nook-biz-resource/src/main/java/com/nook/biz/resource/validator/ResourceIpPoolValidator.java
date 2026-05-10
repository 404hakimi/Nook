package com.nook.biz.resource.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.constant.ResourceErrorCode;
import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.resource.entity.ResourceIpPool;
import com.nook.biz.resource.entity.ResourceIpType;
import com.nook.biz.resource.mapper.ResourceIpPoolMapper;
import com.nook.biz.resource.mapper.ResourceIpTypeMapper;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * IP 池条目业务校验.
 *
 * @author nook
 */
@Component
public class ResourceIpPoolValidator {

    private static final BigDecimal SCORE_MAX = BigDecimal.valueOf(100);

    @Resource
    private ResourceIpPoolMapper resourceIpPoolMapper;
    @Resource
    private ResourceIpTypeMapper resourceIpTypeMapper;

    /**
     * 校验 IP 池条目存在.
     *
     * @param id resource_ip_pool.id
     * @return ResourceIpPool
     */
    public ResourceIpPool validateExists(String id) {
        ResourceIpPool e = resourceIpPoolMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_NOT_FOUND, id);
        }
        return e;
    }

    /**
     * 校验 IP 类型存在.
     *
     * @param ipTypeId resource_ip_type.id
     */
    public void validateIpTypeExists(String ipTypeId) {
        ResourceIpType type = resourceIpTypeMapper.selectById(ipTypeId);
        if (ObjectUtil.isNull(type)) {
            throw new BusinessException(ResourceErrorCode.IP_TYPE_NOT_FOUND, ipTypeId);
        }
    }

    /**
     * 校验 IP 地址在全局唯一; id 用于排除自身 (Update), Create 传 null.
     *
     * @param id        当前行 id (Create 传 null)
     * @param ipAddress IP 地址
     */
    public void validateIpAddressUnique(String id, String ipAddress) {
        if (id == null) {
            if (ObjectUtil.isNotNull(resourceIpPoolMapper.selectByIpAddress(ipAddress))) {
                throw new BusinessException(ResourceErrorCode.IP_POOL_IP_DUPLICATE, ipAddress);
            }
            return;
        }
        if (resourceIpPoolMapper.existsByIpAddressExcludingId(ipAddress, id)) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_IP_DUPLICATE, ipAddress);
        }
    }

    /**
     * Create 入参完整校验: 必填 + 字段范围 + 类型存在 + IP 唯一.
     *
     * @param reqVO save 入参
     */
    public void validateForCreate(ResourceIpPoolSaveReqVO reqVO) {
        if (ObjectUtil.isNull(reqVO)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "ip-pool save 入参不能为空");
        }
        requireBlank(reqVO.getRegion(), "region");
        requireBlank(reqVO.getIpTypeId(), "ipTypeId");
        requireBlank(reqVO.getIpAddress(), "ipAddress");
        requireBlank(reqVO.getSocks5Host(), "socks5Host");
        requireNull(reqVO.getSocks5Port(), "socks5Port");
        requireNull(reqVO.getStatus(), "status");

        validateFieldRanges(reqVO);
        validateIpTypeExists(reqVO.getIpTypeId());
        validateIpAddressUnique(null, reqVO.getIpAddress());
    }

    /**
     * Update 入参字段范围校验; 字段全可空, 唯一性 / 类型存在由 service 在判定字段变更后单独调.
     *
     * @param reqVO save 入参
     */
    public void validateForUpdate(ResourceIpPoolSaveReqVO reqVO) {
        if (ObjectUtil.isNull(reqVO)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "ip-pool save 入参不能为空");
        }
        validateFieldRanges(reqVO);
    }

    private void validateFieldRanges(ResourceIpPoolSaveReqVO r) {
        if (r.getRegion() != null && r.getRegion().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "region 长度需 ≤ 64");
        }
        if (r.getIpTypeId() != null && r.getIpTypeId().length() > 36) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "ipTypeId 长度需 ≤ 36");
        }
        if (r.getIpAddress() != null && r.getIpAddress().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "ipAddress 长度需 ≤ 64");
        }
        if (r.getSocks5Host() != null && r.getSocks5Host().length() > 128) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "socks5Host 长度需 ≤ 128");
        }
        if (r.getSocks5Port() != null && (r.getSocks5Port() < 1 || r.getSocks5Port() > 65535)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "socks5Port 范围 1-65535");
        }
        if (r.getSocks5Username() != null && r.getSocks5Username().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "socks5Username 长度需 ≤ 64");
        }
        if (r.getSocks5Password() != null && r.getSocks5Password().length() > 255) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "socks5Password 长度需 ≤ 255");
        }
        if (r.getStatus() != null && (r.getStatus() < 1 || r.getStatus() > 6)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "status 取值 1-6");
        }
        if (r.getScore() != null
                && (r.getScore().signum() < 0 || r.getScore().compareTo(SCORE_MAX) > 0)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "score 范围 0-100");
        }
        if (r.getScamalyticsScore() != null
                && (r.getScamalyticsScore() < 0 || r.getScamalyticsScore() > 100)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "scamalyticsScore 范围 0-100");
        }
        if (r.getIpqsScore() != null && (r.getIpqsScore() < 0 || r.getIpqsScore() > 100)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "ipqsScore 范围 0-100");
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
