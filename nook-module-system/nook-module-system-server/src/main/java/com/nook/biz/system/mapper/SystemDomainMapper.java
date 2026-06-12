package com.nook.biz.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.entity.SystemDomainDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统域名 Mapper
 *
 * @author nook
 */
@Mapper
public interface SystemDomainMapper extends BaseMapper<SystemDomainDO> {

    default List<SystemDomainDO> selectAllOrdered() {
        return selectList(Wrappers.<SystemDomainDO>lambdaQuery()
                .orderByDesc(SystemDomainDO::getCreatedAt));
    }

    default boolean existsByDomain(String domain) {
        return exists(Wrappers.<SystemDomainDO>lambdaQuery()
                .eq(SystemDomainDO::getDomain, domain));
    }

    default boolean existsByDomainExcludingId(String domain, String excludeId) {
        return exists(Wrappers.<SystemDomainDO>lambdaQuery()
                .eq(SystemDomainDO::getDomain, domain)
                .ne(SystemDomainDO::getId, excludeId));
    }
}
