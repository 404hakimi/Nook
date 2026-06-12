package com.nook.biz.system.api.domain;

import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;
import com.nook.biz.system.entity.SystemDomainDO;
import com.nook.biz.system.service.SystemDomainService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 系统域名 Api 实现类
 *
 * @author nook
 */
@Service
public class SystemDomainApiImpl implements SystemDomainApi {

    @Resource
    private SystemDomainService systemDomainService;

    @Override
    public SystemDomainRespDTO getById(String id) {
        // 查询域名
        SystemDomainDO domain = systemDomainService.getDomain(id);
        // 转换返回
        return SystemDomainApiConvert.INSTANCE.toRespDTO(domain);
    }

    @Override
    public Map<String, String> getDomainMap(Collection<String> ids) {
        return systemDomainService.getDomainMap(ids);
    }
}
