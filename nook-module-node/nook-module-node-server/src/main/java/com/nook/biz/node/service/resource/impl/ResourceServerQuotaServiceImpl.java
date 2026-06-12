package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.controller.resource.vo.ResourceServerQuotaUpdateReqVO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import com.nook.biz.node.mapper.ResourceServerQuotaMapper;
import com.nook.biz.node.service.resource.ResourceServerQuotaService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 服务器额度 Service 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerQuotaServiceImpl implements ResourceServerQuotaService {

    @Resource
    private ResourceServerQuotaMapper resourceServerQuotaMapper;

    @Override
    public ResourceServerQuotaDO get(String serverId) {
        return resourceServerQuotaMapper.selectById(serverId);
    }

    @Override
    public void updateQuota(String serverId, ResourceServerQuotaUpdateReqVO reqVO) {
        resourceServerQuotaMapper.updateQuota(serverId, reqVO.getTotalGb(), reqVO.getUsablePercent(),
                reqVO.getBandwidthMbps(), reqVO.getResetPolicy(), reqVO.getResetDay());
    }
}
