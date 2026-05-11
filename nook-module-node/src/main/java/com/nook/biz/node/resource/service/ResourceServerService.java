package com.nook.biz.node.resource.service;

import com.nook.biz.node.resource.controller.server.vo.ResourceServerPageReqVO;
import com.nook.biz.node.resource.controller.server.vo.ResourceServerSaveReqVO;
import com.nook.biz.node.resource.dto.ServerBriefDTO;
import com.nook.biz.node.resource.entity.ResourceServer;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/** 服务器(出口 VPS)管理. */
public interface ResourceServerService {

    ResourceServer findById(String id);

    /** 不抛错, 仅探测存在性. */
    boolean exists(String id);

    PageResult<ResourceServer> page(ResourceServerPageReqVO reqVO);

    ResourceServer create(ResourceServerSaveReqVO reqVO);

    void update(String id, ResourceServerSaveReqVO reqVO);

    void delete(String id);

    /**
     * 批量按 id 取轻量 brief (name + host); 列表 enrich 用, 不下发 SSH 凭据.
     * 走 mapper.selectBatchIds 一次性查, 避免 N+1. 缺失的 id 不会出现在 map 里.
     */
    Map<String, ServerBriefDTO> loadBriefMap(Collection<String> serverIds);
}
