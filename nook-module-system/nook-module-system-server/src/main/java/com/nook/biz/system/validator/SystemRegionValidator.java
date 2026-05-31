package com.nook.biz.system.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.dal.dataobject.region.SystemRegionDO;
import com.nook.biz.system.dal.mysql.mapper.region.SystemRegionMapper;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 区域字典业务校验
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class SystemRegionValidator {

    private final SystemRegionMapper systemRegionMapper;

    /**
     * 校验区域存在
     *
     * @param code 区域码
     * @return 区域 DO
     */
    public SystemRegionDO validateExists(String code) {
        SystemRegionDO e = systemRegionMapper.selectById(code);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(SystemErrorCode.REGION_NOT_FOUND, code);
        }
        return e;
    }

    /**
     * 校验区域码未被占用 (新增用)
     *
     * @param code 区域码
     */
    public void validateCodeAvailable(String code) {
        if (ObjectUtil.isNotNull(systemRegionMapper.selectById(code))) {
            throw new BusinessException(SystemErrorCode.REGION_CODE_EXISTS, code);
        }
    }
}
