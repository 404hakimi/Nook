package com.nook.biz.system.api.domain;

import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;
import com.nook.biz.system.convert.SystemDomainConvert;
import com.nook.biz.system.service.domain.SystemDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 系统域名 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class SystemDomainApiImpl implements SystemDomainApi {

    private final SystemDomainService systemDomainService;

    @Override
    public SystemDomainRespDTO getById(String id) {
        return SystemDomainConvert.INSTANCE.toRespDTO(systemDomainService.getDomain(id));
    }

    @Override
    public Map<String, String> getDomainMap(Collection<String> ids) {
        return systemDomainService.loadDomainMap(ids);
    }
}
