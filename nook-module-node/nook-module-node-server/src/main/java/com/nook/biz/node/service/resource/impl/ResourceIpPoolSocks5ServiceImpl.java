package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSocks5UpdateReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolSocks5DO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolSocks5Mapper;
import com.nook.biz.node.service.resource.ResourceIpPoolSocks5Service;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.common.utils.object.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IP 池 dante 配置 Service 实现类 (实际限速拆到 capacity 子表)
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceIpPoolSocks5ServiceImpl implements ResourceIpPoolSocks5Service {

    private final ResourceIpPoolSocks5Mapper socks5Mapper;
    private final ResourceIpPoolValidator ipPoolValidator;

    @Override
    public ResourceIpPoolSocks5DO get(String ipId) {
        return socks5Mapper.selectById(ipId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String ipId, ResourceIpPoolSocks5UpdateReqVO reqVO) {
        ipPoolValidator.validateExists(ipId);

        ResourceIpPoolSocks5DO patch = BeanUtils.toBean(reqVO, ResourceIpPoolSocks5DO.class);
        patch.setIpId(ipId);
        // 密码留空 = 保留原值 (前端 update form 不显示密码, 改密码才填)
        if (StrUtil.isBlank(patch.getSocks5Password())) {
            patch.setSocks5Password(null);
        }
        socks5Mapper.updateBySelective(patch);
    }
}
