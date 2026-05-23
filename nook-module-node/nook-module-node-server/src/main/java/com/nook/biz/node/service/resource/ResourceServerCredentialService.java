package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;

import java.util.Collection;
import java.util.Map;

/**
 * 服务器 SSH 凭据 Service 接口
 *
 * @author nook
 */
public interface ResourceServerCredentialService {

    /**
     * 取凭据
     *
     * @param serverId server 主键
     * @return DO; 不存在返 null
     */
    ResourceServerCredentialDO get(String serverId);

    /**
     * 取凭据; 缺失抛 SERVER_NOT_FOUND
     *
     * @param serverId server 主键
     * @return DO
     */
    ResourceServerCredentialDO requireByServerId(String serverId);

    /**
     * 批量取 host
     *
     * @param serverIds server 主键集合
     * @return key=serverId, value=host; 缺失 server 不在 map 里
     */
    Map<String, String> getHostMap(Collection<String> serverIds);

    /**
     * 装机时一次性创建; create 流程内调用 (事务内随主表一起写).
     *
     * @param serverId server 主键
     * @param reqVO    凭据信息
     */
    void create(String serverId, ResourceServerCredentialUpdateReqVO reqVO);

    /**
     * 更新凭据; LIVE 后 host/port 硬锁; 密码空保留原值; 完成后发 ServerCredentialChangedEvent.
     *
     * @param serverId server 主键
     * @param reqVO    待保存
     */
    void update(String serverId, ResourceServerCredentialUpdateReqVO reqVO);
}
