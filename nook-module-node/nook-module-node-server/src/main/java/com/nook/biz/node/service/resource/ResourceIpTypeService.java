package com.nook.biz.node.service.resource;

import com.nook.biz.node.dal.dataobject.resource.ResourceIpTypeDO;

import java.util.List;

/**
 * IP 类型 Service 接口
 *
 * @author nook
 */
public interface ResourceIpTypeService {

    /**
     * 获得所有 IP 类型, 按 sort_order 升序
     *
     * @return IP 类型列表
     */
    List<ResourceIpTypeDO> getIpTypeList();

    /**
     * 获得 IP 类型
     *
     * @param id IP 类型编号
     * @return IP 类型信息
     */
    ResourceIpTypeDO getIpType(String id);
}
