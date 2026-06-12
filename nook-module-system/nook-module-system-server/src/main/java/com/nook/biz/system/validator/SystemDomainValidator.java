package com.nook.biz.system.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.entity.SystemDomainDO;
import com.nook.biz.system.mapper.SystemDomainMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 系统域名业务校验
 *
 * @author nook
 */
@Component
public class SystemDomainValidator {

    @Resource
    private SystemDomainMapper systemDomainMapper;

    /**
     * 校验域名存在
     *
     * @param id 域名ID
     * @return 域名信息
     */
    public SystemDomainDO validateExists(String id) {
        SystemDomainDO e = systemDomainMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(SystemErrorCode.DOMAIN_NOT_FOUND, id);
        }
        return e;
    }

    /**
     * 校验域名唯一; id 非空时排除自身 (Update 不冲突自己)
     *
     * @param id     当前行 id, Create 传 null
     * @param domain 根域名
     */
    public void validateDomainUnique(String id, String domain) {
        boolean dup = id == null
                ? systemDomainMapper.existsByDomain(domain)
                : systemDomainMapper.existsByDomainExcludingId(domain, id);
        if (dup) {
            throw new BusinessException(SystemErrorCode.DOMAIN_DUPLICATE, domain);
        }
    }
}
