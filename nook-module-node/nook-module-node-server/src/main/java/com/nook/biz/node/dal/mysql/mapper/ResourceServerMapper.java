package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 服务器资源 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerMapper extends BaseMapper<ResourceServerDO> {

    default boolean existsByName(String name) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getName, name));
    }

    default boolean existsByNameExcludingId(String name, String excludeId) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getName, name)
                .ne(ResourceServerDO::getId, excludeId));
    }

    /** 按 agent_token 查 server (Agent push 接口鉴权用); 找不到返 null. */
    default ResourceServerDO selectByAgentToken(String agentToken) {
        return selectOne(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getAgentToken, agentToken));
    }

    /**
     * 列表分页. host 过滤需由调用方先查 credential 子表得到 idIn 再传入 (null=不约束).
     */
    default IPage<ResourceServerDO> selectPageByQuery(IPage<ResourceServerDO> page, String name,
                                                      String lifecycleState, String region,
                                                      Collection<String> idIn) {
        return selectPage(page, Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(lifecycleState), ResourceServerDO::getLifecycleState, lifecycleState)
                .eq(StrUtil.isNotBlank(region), ResourceServerDO::getRegion, region)
                .like(StrUtil.isNotBlank(name), ResourceServerDO::getName, name)
                .in(idIn != null, ResourceServerDO::getId, idIn)
                .orderByDesc(ResourceServerDO::getCreatedAt));
    }

    /** 切换 lifecycle_state; 显式 set updated_at. */
    default int updateLifecycleState(String id, String newState) {
        return update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getLifecycleState, newState)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getId, id));
    }
}
