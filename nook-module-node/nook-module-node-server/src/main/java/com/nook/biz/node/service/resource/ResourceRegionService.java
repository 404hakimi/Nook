package com.nook.biz.node.service.resource;

import com.nook.biz.node.dal.dataobject.resource.ResourceRegionDO;

import java.util.List;

/** 区域字典 service; admin 维护 + 表单下拉. */
public interface ResourceRegionService {

    /** 已启用区域列表; 表单下拉用. */
    List<ResourceRegionDO> listEnabled();

    /** 全量列表 (admin 管理用); keyword 模糊 + enabled 精确. */
    List<ResourceRegionDO> list(String keyword, Integer enabled);

    ResourceRegionDO getByCode(String code);
}
