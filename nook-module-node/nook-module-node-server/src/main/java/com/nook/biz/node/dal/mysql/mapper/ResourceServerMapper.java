package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * ResourceServerDO 数据访问.
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

    default boolean existsByHost(String host) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getHost, host));
    }

    default boolean existsByHostExcludingId(String host, String excludeId) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getHost, host)
                .ne(ResourceServerDO::getId, excludeId));
    }

    /** 按 agent_token 查 server (Agent push 接口鉴权用); 找不到返 null. */
    default ResourceServerDO selectByAgentToken(String agentToken) {
        return selectOne(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getAgentToken, agentToken));
    }

    /** domain 是否存在 (LIVE 前置必填的唯一字段). */
    default boolean existsByDomain(String domain) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getDomain, domain));
    }

    default boolean existsByDomainExcludingId(String domain, String excludeId) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getDomain, domain)
                .ne(ResourceServerDO::getId, excludeId));
    }

    /** 分页; keyword 模糊匹 name/host/domain; lifecycleState / region 精确过滤. */
    default IPage<ResourceServerDO> selectPageByQuery(IPage<ResourceServerDO> page, ResourceServerPageReqVO reqVO) {
        return selectPage(page, Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(reqVO.getLifecycleState()), ResourceServerDO::getLifecycleState, reqVO.getLifecycleState())
                .eq(StrUtil.isNotBlank(reqVO.getRegion()), ResourceServerDO::getRegion, reqVO.getRegion())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), q -> q
                        .like(ResourceServerDO::getName, reqVO.getKeyword())
                        .or().like(ResourceServerDO::getHost, reqVO.getKeyword())
                        .or().like(ResourceServerDO::getDomain, reqVO.getKeyword()))
                .orderByDesc(ResourceServerDO::getCreatedAt));
    }

    /** 切换 lifecycle_state (admin 上线 / 退役); 显式 set updated_at. */
    default int updateLifecycleState(String id, String newState) {
        return update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getLifecycleState, newState)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getId, id));
    }
}
