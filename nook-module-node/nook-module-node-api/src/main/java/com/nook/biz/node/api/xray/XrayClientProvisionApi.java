package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayClientProvisionDTO;

/**
 * Xray 客户端开通 / 吊销跨模块契约.
 *
 * @author nook
 */
public interface XrayClientProvisionApi {

    /**
     * 开通客户端 (占用落地机 + 远端节点下发, 同步等终态)
     *
     * @param req 开通入参
     * @return 新建的客户端ID
     */
    String provision(XrayClientProvisionDTO req);

    /**
     * 吊销客户端 (远端清理 + 落地机归还为可分配)
     *
     * @param clientId 客户端ID
     */
    void revoke(String clientId);

    /**
     * 停服客户端: 置为已停, 保留客户端记录与落地机占用, 续费 / 重置后可恢复
     *
     * @param clientId 客户端ID
     */
    void stop(String clientId);

    /**
     * 复活客户端: 置为运行, 落地机占用不变
     *
     * @param clientId 客户端ID
     */
    void resume(String clientId);

    /**
     * 改客户端所在线路机 (故障切换): 只改线路机, 落地与 uuid 不变
     *
     * @param clientId    客户端ID
     * @param newServerId 新线路机ID
     */
    void rebindFrontline(String clientId, String newServerId);
}
