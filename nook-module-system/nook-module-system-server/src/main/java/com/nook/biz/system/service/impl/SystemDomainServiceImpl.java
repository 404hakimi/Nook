package com.nook.biz.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.xray.XrayInstallApi;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.controller.domain.vo.SystemDomainCreateReqVO;
import com.nook.biz.system.controller.domain.vo.SystemDomainUpdateReqVO;
import com.nook.biz.system.entity.SystemDomainDO;
import com.nook.biz.system.mapper.SystemDomainMapper;
import com.nook.biz.system.service.SystemDomainService;
import com.nook.biz.system.validator.SystemDomainValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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
public class SystemDomainServiceImpl implements SystemDomainService {

    @Resource
    private SystemDomainMapper systemDomainMapper;
    @Resource
    private SystemDomainValidator systemDomainValidator;
    @Resource
    private XrayInstallApi xrayInstallApi;

    @Override
    public List<SystemDomainDO> getDomainList() {
        return systemDomainMapper.selectAllOrdered();
    }

    @Override
    public SystemDomainDO getDomain(String id) {
        return systemDomainValidator.validateExists(id);
    }

    @Override
    public String createDomain(SystemDomainCreateReqVO reqVO) {
        // 校验域名唯一
        systemDomainValidator.validateDomainUnique(null, reqVO.getDomain());
        // 插入域名
        SystemDomainDO entity = BeanUtils.toBean(reqVO, SystemDomainDO.class);
        systemDomainMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateDomain(String id, SystemDomainUpdateReqVO reqVO) {
        // 校验存在 + 域名唯一 (排除自身)
        SystemDomainDO existing = systemDomainValidator.validateExists(id);
        systemDomainValidator.validateDomainUnique(id, reqVO.getDomain());
        // 已被线路机绑定时禁止改根域串 (已装机的 cert / xray inbound 仍用旧 FQDN, 改串会致部署漂移); CF 配置 / 备注仍可改
        if (!StrUtil.equals(existing.getDomain(), reqVO.getDomain()) && xrayInstallApi.isDomainBound(id)) {
            throw new BusinessException(SystemErrorCode.DOMAIN_RENAME_BOUND, existing.getDomain());
        }
        // 更新域名
        SystemDomainDO entity = BeanUtils.toBean(reqVO, SystemDomainDO.class);
        entity.setId(id);
        systemDomainMapper.updateById(entity);
    }

    @Override
    public void deleteDomain(String id) {
        // 校验存在
        systemDomainValidator.validateExists(id);
        // 删除域名
        systemDomainMapper.deleteById(id);
    }

    @Override
    public Map<String, String> getDomainMap(Collection<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        // 批量查域名
        List<SystemDomainDO> domains = systemDomainMapper.selectBatchIds(ids);
        // 提取域名ID → 根域名
        return CollectionUtils.convertMap(domains, SystemDomainDO::getId, SystemDomainDO::getDomain);
    }
}
