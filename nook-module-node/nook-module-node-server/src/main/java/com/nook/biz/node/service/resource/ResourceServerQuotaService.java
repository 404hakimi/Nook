package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerQuotaUpdateReqVO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;

/**
 * 服务器容量 Service 接口
 *
 * @author nook
 */
public interface ResourceServerQuotaService {

    /**
     * 获得服务器容量
     *
     * @param serverId 服务器编号
     * @return 服务器容量
     */
    ResourceServerQuotaDO get(String serverId);

    /**
     * 更新业务阈值
     *
     * @param serverId 服务器编号
     * @param reqVO    阈值入参
     */
    void updateQuota(String serverId, ResourceServerQuotaUpdateReqVO reqVO);
}
