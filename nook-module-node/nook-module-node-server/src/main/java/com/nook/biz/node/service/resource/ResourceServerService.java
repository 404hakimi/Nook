package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * 资源服务器 Service 接口
 *
 * @author nook
 */
public interface ResourceServerService {

    /**
     * 创建服务器 (事务内一次性写 主表 + credential + billing + dns + capacity/runtime 占位).
     *
     * @param createReqVO 创建参数
     * @return server.id
     */
    String createServer(ResourceServerCreateReqVO createReqVO);

    /**
     * 更新核心字段 (lifecycle 走 transitionLifecycle; SSH/账面/DNS 走各自子 service).
     *
     * @param id    server.id
     * @param reqVO 待更新
     */
    void updateCore(String id, ResourceServerCoreUpdateReqVO reqVO);

    /**
     * 删除服务器 (软删主表; 子表保留, 数据完整性靠 server.deleted=1 过滤).
     *
     * @param id server.id
     */
    void deleteServer(String id);

    /**
     * 按 id 查服务器.
     *
     * @param id server.id
     * @return DO; 不存在返 null
     */
    ResourceServerDO getServer(String id);

    /**
     * 分页 (主表 + host 子表联合过滤).
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<ResourceServerDO> getServerPage(ResourceServerPageReqVO pageReqVO);

    /**
     * 批量按 id 取 DO map.
     *
     * @param ids server.id 集合
     * @return Map of serverId → DO
     */
    Map<String, ResourceServerDO> getServerMap(Collection<String> ids);

    /**
     * 批量按 id 取 name map.
     *
     * @param ids server.id 集合
     * @return Map of serverId → name
     */
    Map<String, String> getServerNameMap(Collection<String> ids);

    /**
     * lifecycle 流转 (INSTALLING ↔ READY ↔ LIVE → RETIRED; RETIRED 可回 LIVE).
     *
     * @param id       server.id
     * @param newState 目标态
     */
    void transitionLifecycle(String id, String newState);
}
