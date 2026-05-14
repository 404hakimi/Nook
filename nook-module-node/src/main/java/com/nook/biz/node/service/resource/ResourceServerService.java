package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerSaveReqVO;
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
     * 创建服务器
     *
     * @param createReqVO 服务器信息
     * @return 服务器编号
     */
    String createServer(ResourceServerSaveReqVO createReqVO);

    /**
     * 更新服务器; 改动后发布 ServerCredentialChangedEvent.
     *
     * @param id          服务器编号
     * @param updateReqVO 服务器信息
     */
    void updateServer(String id, ResourceServerSaveReqVO updateReqVO);

    /**
     * 删除服务器; 同时发布 ServerCredentialChangedEvent.
     *
     * @param id 服务器编号
     */
    void deleteServer(String id);

    /**
     * 按 id 查服务器; 必查到走 {@link com.nook.biz.node.validator.ResourceServerValidator#validateExists}.
     *
     * @param id 服务器编号
     * @return 服务器; 不存在返 null
     */
    ResourceServerDO getServer(String id);

    /**
     * 获得服务器分页
     *
     * @param pageReqVO 分页条件
     * @return 服务器分页
     */
    PageResult<ResourceServerDO> getServerPage(ResourceServerPageReqVO pageReqVO);

    /**
     * 批量获得服务器 Map
     *
     * @param ids 服务器编号集合
     * @return Map of serverId → 服务器
     */
    Map<String, ResourceServerDO> getServerMap(Collection<String> ids);

    /**
     * 批量获得服务器名 Map
     *
     * @param ids 服务器编号集合
     * @return Map of serverId → name (name 缺失 fallback host)
     */
    Map<String, String> getServerNameMap(Collection<String> ids);
}
