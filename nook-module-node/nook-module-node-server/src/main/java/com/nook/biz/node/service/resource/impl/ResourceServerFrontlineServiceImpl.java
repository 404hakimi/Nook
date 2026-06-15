package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.frontline.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;
import com.nook.biz.node.convert.resource.ResourceServerFrontlineConvert;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.lifecycle.ServerLifecycleManager;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 线路机 Service 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerFrontlineServiceImpl implements ResourceServerFrontlineService {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ServerLifecycleManager serverLifecycleManager;

    @Override
    public PageResult<ServerFrontlineListItemRespVO> getFrontlinePage(ResourceServerPageReqVO reqVO) {
        // 连表分页查列表项视图
        IPage<ServerFrontlineListItemRespVO> result = resourceServerMapper.selectFrontlinePage(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()),
                reqVO.getName(), reqVO.getHost(), reqVO.getLifecycleState(), reqVO.getRegionCodes(),
                ResourceServerTypeEnum.FRONTLINE.getState());
        // 按心跳推导在线态填充
        LocalDateTime now = LocalDateTime.now();
        result.getRecords().forEach(vo -> ResourceServerFrontlineConvert.fillOnlineState(vo, now));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public ServerFrontlineListItemRespVO getServerRuntimeDetail(String serverId) {
        ServerFrontlineListItemRespVO vo = resourceServerMapper.selectServerRuntimeDetail(serverId);
        if (ObjectUtil.isNotNull(vo)) {
            ResourceServerFrontlineConvert.fillOnlineState(vo, LocalDateTime.now());
        }
        return vo;
    }

    @Override
    public void transitionLifecycle(String id, String newState) {
        // 校验存在
        ResourceServerDO srv = resourceServerValidator.validateExists(id);
        // 委托 manager: 幂等 + 流转表校验 + 按类型守卫 + 落状态
        serverLifecycleManager.transition(srv, newState);
    }
}
