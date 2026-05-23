package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDnsDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 服务器 DNS 绑定 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerDnsMapper extends BaseMapper<ResourceServerDnsDO> {

    default boolean existsByDomain(String domain) {
        if (StrUtil.isBlank(domain)) return false;
        return exists(Wrappers.<ResourceServerDnsDO>lambdaQuery()
                .eq(ResourceServerDnsDO::getDomain, domain));
    }

    default boolean existsByDomainExcludingId(String domain, String excludeServerId) {
        if (StrUtil.isBlank(domain)) return false;
        return exists(Wrappers.<ResourceServerDnsDO>lambdaQuery()
                .eq(ResourceServerDnsDO::getDomain, domain)
                .ne(ResourceServerDnsDO::getServerId, excludeServerId));
    }

    default int updateBySelective(ResourceServerDnsDO patch) {
        return update(patch, Wrappers.<ResourceServerDnsDO>lambdaUpdate()
                .set(ResourceServerDnsDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDnsDO::getServerId, patch.getServerId()));
    }
}
