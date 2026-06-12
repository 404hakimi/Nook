package com.nook.biz.system.api.iptype;

import com.nook.biz.system.api.iptype.dto.SystemIpTypeRespDTO;
import com.nook.biz.system.entity.SystemIpTypeDO;
import com.nook.biz.system.service.SystemIpTypeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * IP 类型 Api 实现类
 *
 * @author nook
 */
@Service
public class SystemIpTypeApiImpl implements SystemIpTypeApi {

    @Resource
    private SystemIpTypeService systemIpTypeService;

    @Override
    public SystemIpTypeRespDTO getById(String id) {
        // 查询 IP 类型
        SystemIpTypeDO type = systemIpTypeService.getIpType(id);
        // 转换返回
        return SystemIpTypeApiConvert.INSTANCE.toRespDTO(type);
    }

    @Override
    public Map<String, String> getNameMap(Collection<String> ids) {
        return systemIpTypeService.getNameMap(ids);
    }
}
