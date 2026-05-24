package com.nook.biz.node.service.resource;

import com.nook.biz.node.dal.dataobject.resource.ResourceRegionDO;

import java.util.List;

/**
 * 资源区域 Service 接口
 *
 * @author nook
 */
public interface ResourceRegionService {

    /**
     * 获得已启用区域列表
     *
     * @return 已启用区域列表
     */
    List<ResourceRegionDO> listEnabled();

    /**
     * 获得区域列表
     *
     * @param keyword 关键字
     * @param enabled 启用状态
     * @return 区域列表
     */
    List<ResourceRegionDO> list(String keyword, Integer enabled);

    /**
     * 按 code 获得区域
     *
     * @param code 区域 code
     * @return 区域
     */
    ResourceRegionDO getByCode(String code);
}
