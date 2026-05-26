package com.nook.biz.system.service.iptype;

import com.nook.biz.system.dal.dataobject.iptype.SystemIpTypeDO;

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
     * @param id IP 类型编号
     * @return IP 类型信息
     */
    SystemIpTypeDO getIpType(String id);

    /**
     * 按 id 批量拉 IP 类型展示名
     *
     * @param ids IP 类型编号集合
     * @return key=id, value=name
     */
    Map<String, String> loadNameMap(Collection<String> ids);
}
