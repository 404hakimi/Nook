package com.nook.biz.system.api.region;

import com.nook.biz.system.service.SystemRegionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 区域字典 Api 实现类
 *
 * @author nook
 */
@Service
public class SystemRegionApiImpl implements SystemRegionApi {

    @Resource
    private SystemRegionService systemRegionService;

    @Override
    public boolean exists(String code) {
        return systemRegionService.exists(code);
    }

    @Override
    public Map<String, String> getRegionDisplayNameMap(Collection<String> codes) {
        return systemRegionService.getDisplayNameMap(codes);
    }
}
