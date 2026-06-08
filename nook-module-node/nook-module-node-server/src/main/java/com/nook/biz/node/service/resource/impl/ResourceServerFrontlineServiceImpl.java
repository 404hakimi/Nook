package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.frontline.ResourceServerFrontlineUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.frontline.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;
import com.nook.biz.node.convert.resource.ResourceServerFrontlineConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerFrontlineMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.biz.node.validator.ResourceServerFrontlineValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.biz.node.validator.ServerLifecycleValidator;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 线路机扩展 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerFrontlineServiceImpl implements ResourceServerFrontlineService {

    @Resource
    private ResourceServerFrontlineMapper resourceServerFrontlineMapper;
    @Resource
    private ResourceServerFrontlineValidator resourceServerFrontlineValidator;
    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ServerLifecycleValidator serverLifecycleValidator;

    @Override
    public ResourceServerFrontlineDO get(String serverId) {
        return resourceServerFrontlineMapper.selectById(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(String serverId, ResourceServerFrontlineUpdateReqVO reqVO) {
        resourceServerFrontlineValidator.validateDomainUnique(null,
                ObjectUtil.isNull(reqVO) ? null : reqVO.getDomain());
        ResourceServerFrontlineDO entity = ObjectUtil.isNull(reqVO)
                ? new ResourceServerFrontlineDO()
                : BeanUtils.toBean(reqVO, ResourceServerFrontlineDO.class);
        entity.setServerId(serverId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resourceServerFrontlineMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String serverId, ResourceServerFrontlineUpdateReqVO reqVO) {
        resourceServerFrontlineValidator.validateExists(serverId);
        resourceServerFrontlineValidator.validateDomainUnique(serverId, reqVO.getDomain());
        ResourceServerFrontlineDO patch = BeanUtils.toBean(reqVO, ResourceServerFrontlineDO.class);
        patch.setServerId(serverId);
        resourceServerFrontlineMapper.updateBySelective(patch);
    }

    @Override
    public PageResult<ServerFrontlineListItemRespVO> getFrontlinePage(ResourceServerPageReqVO reqVO) {
        IPage<ServerFrontlineListItemRespVO> result = resourceServerMapper.selectFrontlinePage(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()),
                reqVO.getName(), reqVO.getHost(), reqVO.getLifecycleState(), reqVO.getRegionCodes(),
                ResourceServerTypeEnum.FRONTLINE.getState());
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
    @Transactional(rollbackFor = Exception.class)
    public void transitionLifecycle(String id, String newState) {
        ResourceServerDO srv = resourceServerValidator.validateExists(id);
        if (StrUtil.equals(srv.getLifecycleState(), newState)) {
            return;
        }
        serverLifecycleValidator.validateTransitionTable(srv, newState);
        // 线路机上线前置: 域名必填
        if (ResourceServerLifecycleEnum.LIVE.matches(newState)) {
            serverLifecycleValidator.validateFrontlineDomainReady(id);
        }
        resourceServerMapper.updateLifecycleState(id, newState);
        log.info("[frontline] LIFECYCLE id={} {} → {}", id, srv.getLifecycleState(), newState);
    }
}
