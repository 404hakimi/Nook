package com.nook.biz.system.api.iptype;

import com.nook.biz.system.api.iptype.dto.SystemIpTypeRespDTO;

import java.util.Collection;
import java.util.Map;

/**
 * IP 类型 Api 接口
 *
 * @author nook
 */
public interface SystemIpTypeApi {

    /**
     * 按 id 获得 IP 类型
     *
     * @param id IP 类型编号
     * @return IP 类型; 不存在抛 BusinessException
     */
    SystemIpTypeRespDTO getById(String id);

    /**
     * 按 id 批量拉 IP 类型展示名
     *
     * @param ids IP 类型编号集合
     * @return key=id, value=name; 缺失 id 不在 map 里
     */
    Map<String, String> getNameMap(Collection<String> ids);
}
