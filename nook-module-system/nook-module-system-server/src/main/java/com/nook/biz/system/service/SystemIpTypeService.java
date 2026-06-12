package com.nook.biz.system.service;

import com.nook.biz.system.entity.SystemIpTypeDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * IP 类型 Service 接口
 *
 * @author nook
 */
public interface SystemIpTypeService {

    /**
     * 获得 IP 类型列表
     *
     * @return IP 类型列表
     */
    List<SystemIpTypeDO> getIpTypeList();

    /**
     * 获得 IP 类型
     *
     * @param id IP 类型ID
     * @return IP 类型信息
     */
    SystemIpTypeDO getIpType(String id);

    /**
     * 根据ID批量查询 IP 类型展示名 (key=ID, value=展示名; 缺失不进 map)
     *
     * @param ids IP 类型ID集合
     * @return Map<String, String>
     */
    Map<String, String> getNameMap(Collection<String> ids);
}
