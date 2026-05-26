package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 线路机扩展 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerFrontlineMapper extends BaseMapper<ResourceServerFrontlineDO> {

    /** 按 server_id 查; 不存在返 null. */
    default ResourceServerFrontlineDO selectByServerId(String serverId) {
        return selectById(serverId);
    }

    /** 按 domain 查唯一; 录入查重用. */
    default ResourceServerFrontlineDO selectByDomain(String domain) {
        return selectOne(Wrappers.<ResourceServerFrontlineDO>lambdaQuery()
                .eq(ResourceServerFrontlineDO::getDomain, domain));
    }

    /** 是否已存在该 domain. */
    default boolean existsByDomain(String domain) {
        if (StrUtil.isBlank(domain)) return false;
        return exists(Wrappers.<ResourceServerFrontlineDO>lambdaQuery()
                .eq(ResourceServerFrontlineDO::getDomain, domain));
    }

    /** 排除指定 server 后是否还有同 domain (Update 查重用). */
    default boolean existsByDomainExcludingId(String domain, String excludeServerId) {
        if (StrUtil.isBlank(domain)) return false;
        return exists(Wrappers.<ResourceServerFrontlineDO>lambdaQuery()
                .eq(ResourceServerFrontlineDO::getDomain, domain)
                .ne(ResourceServerFrontlineDO::getServerId, excludeServerId));
    }

    /** 增量更新; updated_at 显式 set 防 Wrapper 跳过 fill. */
    default int updateBySelective(ResourceServerFrontlineDO patch) {
        return update(patch, Wrappers.<ResourceServerFrontlineDO>lambdaUpdate()
                .set(ResourceServerFrontlineDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerFrontlineDO::getServerId, patch.getServerId()));
    }
}
