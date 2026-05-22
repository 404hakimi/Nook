package com.nook.biz.node.api.resource;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.api.resource.dto.ResourceServerPageReqDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资源服务器 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerApiImpl implements ResourceServerApi {

    private final ResourceServerValidator serverValidator;
    private final ResourceServerMapper resourceServerMapper;

    @Override
    public ResourceServerRespDTO validateExists(String serverId) {
        return BeanUtils.toBean(serverValidator.validateExists(serverId), ResourceServerRespDTO.class);
    }

    @Override
    public ResourceServerRespDTO getByAgentToken(String agentToken) {
        ResourceServerDO srv = resourceServerMapper.selectByAgentToken(agentToken);
        return srv == null ? null : BeanUtils.toBean(srv, ResourceServerRespDTO.class);
    }

    @Override
    public List<ResourceServerRespDTO> listAll() {
        List<ResourceServerDO> servers = resourceServerMapper.selectList(
                Wrappers.<ResourceServerDO>lambdaQuery().eq(ResourceServerDO::getDeleted, 0));
        return BeanUtils.toBean(servers, ResourceServerRespDTO.class);
    }

    @Override
    public PageResult<ResourceServerRespDTO> page(ResourceServerPageReqDTO req) {
        IPage<ResourceServerDO> page = resourceServerMapper.selectPage(
                Page.of(req.getPageNo(), req.getPageSize()),
                Wrappers.<ResourceServerDO>lambdaQuery()
                        .eq(StrUtil.isNotBlank(req.getLifecycleState()), ResourceServerDO::getLifecycleState, req.getLifecycleState())
                        .eq(StrUtil.isNotBlank(req.getRegion()), ResourceServerDO::getRegion, req.getRegion())
                        .and(StrUtil.isNotBlank(req.getName()), q -> q
                                .like(ResourceServerDO::getName, req.getName())
                                .or().like(ResourceServerDO::getDomain, req.getName()))
                        .like(StrUtil.isNotBlank(req.getHost()), ResourceServerDO::getHost, req.getHost())
                        .orderByDesc(ResourceServerDO::getCreatedAt));
        return PageResult.of(page.getTotal(), BeanUtils.toBean(page.getRecords(), ResourceServerRespDTO.class));
    }
}
