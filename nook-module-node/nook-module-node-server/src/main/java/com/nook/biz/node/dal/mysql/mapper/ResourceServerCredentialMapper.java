package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务器 SSH 凭据 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerCredentialMapper extends BaseMapper<ResourceServerCredentialDO> {

    default boolean existsByHost(String host) {
        return exists(Wrappers.<ResourceServerCredentialDO>lambdaQuery()
                .eq(ResourceServerCredentialDO::getHost, host));
    }

    default boolean existsByHostExcludingId(String host, String excludeServerId) {
        return exists(Wrappers.<ResourceServerCredentialDO>lambdaQuery()
                .eq(ResourceServerCredentialDO::getHost, host)
                .ne(ResourceServerCredentialDO::getServerId, excludeServerId));
    }

    /** host 模糊匹的行 (列表分页 host 过滤前置查匹配 serverId 用). */
    default List<ResourceServerCredentialDO> selectByHostLike(String hostLike) {
        return selectList(Wrappers.<ResourceServerCredentialDO>lambdaQuery()
                .like(ResourceServerCredentialDO::getHost, hostLike));
    }

    /** 部分字段更新; 显式 set updated_at. */
    default int updateBySelective(ResourceServerCredentialDO patch) {
        return update(patch, Wrappers.<ResourceServerCredentialDO>lambdaUpdate()
                .set(ResourceServerCredentialDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerCredentialDO::getServerId, patch.getServerId()));
    }
}
