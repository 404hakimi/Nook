package com.nook.biz.system.dal.mysql.mapper.domain;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.dal.dataobject.domain.SystemDomainDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统域名 Mapper
 *
 * @author nook
 */
@Mapper
public interface SystemDomainMapper extends BaseMapper<SystemDomainDO> {

    /** 全量列出, 按创建倒序 (域名管理页 + 装机下拉用). */
    default List<SystemDomainDO> selectAllOrdered() {
        return selectList(Wrappers.<SystemDomainDO>lambdaQuery()
                .orderByDesc(SystemDomainDO::getCreatedAt));
    }

    /** 按域名查 (唯一校验); 无返 null. */
    default SystemDomainDO selectByDomain(String domain) {
        return selectOne(Wrappers.<SystemDomainDO>lambdaQuery()
                .eq(SystemDomainDO::getDomain, domain)
                .last("LIMIT 1"));
    }
}
