package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.frontline.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;
import com.nook.biz.node.convert.resource.ResourceServerFrontlineConvert;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.ServerLifecycleValidator;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 线路机 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerFrontlineServiceImpl implements ResourceServerFrontlineService {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ServerLifecycleValidator serverLifecycleValidator;

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
        // 校验存在; 状态未变直接幂等返回
        ResourceServerDO srv = resourceServerValidator.validateExists(id);
        if (StrUtil.equals(srv.getLifecycleState(), newState)) {
            return;
        }
        // 校验流转表; 上线另查域名已绑定
        serverLifecycleValidator.validateTransitionTable(srv, newState);
        if (ResourceServerLifecycleEnum.LIVE.matches(newState)) {
            serverLifecycleValidator.validateFrontlineDomainReady(id);
        }
        // 落新状态
        resourceServerMapper.updateLifecycleState(id, newState);
        log.info("[transitionLifecycle] 线路机生命周期切换: id={}, {} → {}", id, srv.getLifecycleState(), newState);
    }
}
