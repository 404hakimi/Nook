package com.nook.biz.resource.service;

import com.nook.biz.resource.entity.ResourceIpType;

import java.util.List;

/** IP 类型只读查询服务；CRUD 暂不开放，初始数据由 99_seed.sql 提供。 */
public interface ResourceIpTypeService {

    /** 全量列出按 sort_order 升序。 */
    List<ResourceIpType> listAll();

    /** 按 id 查；不存在抛 BusinessException。 */
    ResourceIpType findById(String id);
}
