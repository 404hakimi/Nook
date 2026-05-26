package com.nook.biz.node.service.resource.impl;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 线路机扩展 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceServerFrontlineServiceImpl implements ResourceServerFrontlineService {

    private final ResourceServerFrontlineMapper frontlineMapper;
    private final ResourceServerFrontlineValidator frontlineValidator;
    private final ResourceServerCredentialMapper credentialMapper;
    private final ResourceServerRuntimeMapper runtimeMapper;
    private final ResourceServerCapacityMapper capacityMapper;
    private final XrayServerMapper xrayServerMapper;

    @Override
    public ResourceServerFrontlineDO get(String serverId) {
        return frontlineMapper.selectById(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(String serverId, ResourceServerFrontlineUpdateReqVO reqVO) {
        frontlineValidator.validateDomainUnique(null, reqVO == null ? null : reqVO.getDomain());
        ResourceServerFrontlineDO entity = reqVO == null
                ? new ResourceServerFrontlineDO()
                : BeanUtils.toBean(reqVO, ResourceServerFrontlineDO.class);
        entity.setServerId(serverId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        frontlineMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String serverId, ResourceServerFrontlineUpdateReqVO reqVO) {
        frontlineValidator.validateExists(serverId);
        frontlineValidator.validateDomainUnique(serverId, reqVO.getDomain());
        ResourceServerFrontlineDO patch = BeanUtils.toBean(reqVO, ResourceServerFrontlineDO.class);
        patch.setServerId(serverId);
        frontlineMapper.updateBySelective(patch);
    }

    @Override
    public RuntimeBundle loadRuntimeBundleSingle(String serverId) {
        return batchLoadRuntimeBundle(java.util.Set.of(serverId));
    }

    @Override
    public RuntimeBundle batchLoadRuntimeBundle(Collection<String> serverIds) {
        if (CollectionUtils.isAnyEmpty(serverIds)) {
            Map<String, ResourceServerCredentialDO> emptyCred = Collections.emptyMap();
            Map<String, ResourceServerRuntimeDO> emptyRt = Collections.emptyMap();
            Map<String, ResourceServerCapacityDO> emptyCap = Collections.emptyMap();
            Map<String, XrayServerDO> emptyXray = Collections.emptyMap();
            return new RuntimeBundle(emptyCred, emptyRt, emptyCap, emptyXray);
        }
        Map<String, ResourceServerCredentialDO> creds = CollectionUtils.convertMap(
                credentialMapper.selectBatchIds(serverIds), ResourceServerCredentialDO::getServerId);
        Map<String, ResourceServerRuntimeDO> runtimes = CollectionUtils.convertMap(
                runtimeMapper.selectBatchIds(serverIds), ResourceServerRuntimeDO::getServerId);
        Map<String, ResourceServerCapacityDO> caps = CollectionUtils.convertMap(
                capacityMapper.selectBatchIds(serverIds), ResourceServerCapacityDO::getServerId);
        Map<String, XrayServerDO> xrays = CollectionUtils.convertMap(
                xrayServerMapper.selectBatchIds(serverIds), XrayServerDO::getServerId);
        return new RuntimeBundle(creds, runtimes, caps, xrays);
    }
}
