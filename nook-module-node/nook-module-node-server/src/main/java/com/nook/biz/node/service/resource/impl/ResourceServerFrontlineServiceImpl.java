package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.resource.vo.ResourceServerFrontlineUpdateReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCredentialMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerFrontlineMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayServerMapper;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.biz.node.validator.ResourceServerFrontlineValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 线路机扩展 Service 实现类
 *
 * @author nook
 */
@Service
public class ResourceServerFrontlineServiceImpl implements ResourceServerFrontlineService {

    @Resource
    private ResourceServerFrontlineMapper resourceServerFrontlineMapper;
    @Resource
    private ResourceServerFrontlineValidator resourceServerFrontlineValidator;
    @Resource
    private ResourceServerCredentialMapper resourceServerCredentialMapper;
    @Resource
    private ResourceServerRuntimeMapper resourceServerRuntimeMapper;
    @Resource
    private ResourceServerCapacityMapper resourceServerCapacityMapper;
    @Resource
    private XrayServerMapper xrayServerMapper;

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
    public RuntimeBundle loadRuntimeBundleSingle(String serverId) {
        return this.batchLoadRuntimeBundle(Set.of(serverId));
    }

    @Override
    public RuntimeBundle batchLoadRuntimeBundle(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) {
            return new RuntimeBundle(Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, ResourceServerCredentialDO> creds = CollectionUtils.convertMap(
                resourceServerCredentialMapper.selectBatchIds(serverIds), ResourceServerCredentialDO::getServerId);
        Map<String, ResourceServerRuntimeDO> runtimes = CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(serverIds), ResourceServerRuntimeDO::getServerId);
        Map<String, ResourceServerCapacityDO> caps = CollectionUtils.convertMap(
                resourceServerCapacityMapper.selectBatchIds(serverIds), ResourceServerCapacityDO::getServerId);
        Map<String, XrayServerDO> xrays = CollectionUtils.convertMap(
                xrayServerMapper.selectBatchIds(serverIds), XrayServerDO::getServerId);
        return new RuntimeBundle(creds, runtimes, caps, xrays);
    }
}
