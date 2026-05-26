package com.nook.biz.system.api.iptype;

import com.nook.biz.system.api.iptype.dto.SystemIpTypeRespDTO;
import com.nook.biz.system.dal.dataobject.iptype.SystemIpTypeDO;
import com.nook.biz.system.service.iptype.SystemIpTypeService;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * IP 类型 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class SystemIpTypeApiImpl implements SystemIpTypeApi {

    private final SystemIpTypeService systemIpTypeService;

    @Override
    public SystemIpTypeRespDTO getById(String id) {
        SystemIpTypeDO type = systemIpTypeService.getIpType(id);
        return BeanUtils.toBean(type, SystemIpTypeRespDTO.class);
    }

    @Override
    public Map<String, String> getNameMap(Collection<String> ids) {
        return systemIpTypeService.loadNameMap(ids);
    }
}
