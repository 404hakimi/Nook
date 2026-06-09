package com.nook.biz.system.service.domain.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.controller.domain.vo.SystemDomainSaveReqVO;
import com.nook.biz.system.dal.dataobject.domain.SystemDomainDO;
import com.nook.biz.system.dal.mysql.mapper.domain.SystemDomainMapper;
import com.nook.biz.system.service.domain.SystemDomainService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 系统域名 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class SystemDomainServiceImpl implements SystemDomainService {

    private final SystemDomainMapper systemDomainMapper;

    @Override
    public List<SystemDomainDO> getDomainList() {
        return systemDomainMapper.selectAllOrdered();
    }

    @Override
    public SystemDomainDO getDomain(String id) {
        SystemDomainDO domain = systemDomainMapper.selectById(id);
        if (ObjectUtil.isNull(domain)) {
            throw new BusinessException(SystemErrorCode.DOMAIN_NOT_FOUND, id);
        }
        return domain;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDomain(SystemDomainSaveReqVO reqVO) {
        this.validateDomainUnique(null, reqVO.getDomain());
        SystemDomainDO entity = BeanUtils.toBean(reqVO, SystemDomainDO.class);
        entity.setId(null); // 由 ASSIGN_UUID 生成
        systemDomainMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDomain(SystemDomainSaveReqVO reqVO) {
        this.getDomain(reqVO.getId());
        this.validateDomainUnique(reqVO.getId(), reqVO.getDomain());
        SystemDomainDO patch = BeanUtils.toBean(reqVO, SystemDomainDO.class);
        systemDomainMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDomain(String id) {
        this.getDomain(id);
        systemDomainMapper.deleteById(id);
    }

    @Override
    public Map<String, String> loadDomainMap(Collection<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        return CollectionUtils.convertMap(
                systemDomainMapper.selectBatchIds(ids), SystemDomainDO::getId, SystemDomainDO::getDomain);
    }

    /** 域名全局唯一 (排除自身). */
    private void validateDomainUnique(String excludeId, String domain) {
        SystemDomainDO existing = systemDomainMapper.selectByDomain(domain);
        if (ObjectUtil.isNotNull(existing) && !existing.getId().equals(excludeId)) {
            throw new BusinessException(SystemErrorCode.DOMAIN_DUPLICATE, domain);
        }
    }
}
