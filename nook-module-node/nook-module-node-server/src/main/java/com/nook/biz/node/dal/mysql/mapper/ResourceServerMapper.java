package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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

    /** 是否已存在指定 ipAddress (Create 唯一校验). */
    default boolean existsByIpAddress(String ipAddress) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getIpAddress, ipAddress));
    }

    /** 排除指定 id 后是否还有同 ipAddress (Update 唯一校验). */
    default boolean existsByIpAddressExcludingId(String ipAddress, String excludeId) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getIpAddress, ipAddress)
                .ne(ResourceServerDO::getId, excludeId));
    }

    /** 按 server_type 拉全表 (summary 统计用; 小规模 OK). */
    default List<ResourceServerDO> selectByServerType(String serverType) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, serverType));
    }

    /** 区域内 LIVE 落地机主表 (选址第一步, 区域过滤把范围缩到一个机房). */
    default List<ResourceServerDO> selectLiveLandingsByRegion(String region) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.LANDING.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState())
                .eq(ResourceServerDO::getRegion, region));
    }

    /** 多区域内 LIVE 落地机主表 (批量算容量用; 一次覆盖一批套餐涉及的所有区域). */
    default List<ResourceServerDO> selectLiveLandingsByRegions(Collection<String> regions) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.LANDING.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState())
                .in(ResourceServerDO::getRegion, regions));
    }

    /**
     * 列表分页. ipAddress 直接 LIKE 主表; idIn (来自子表预过滤如 landing.status) 可选作 id 集合过滤;
     * serverType (frontline / landing) 可选.
     */
    default IPage<ResourceServerDO> selectPageByQuery(IPage<ResourceServerDO> page, String name,
                                                      String lifecycleState, String region,
                                                      String ipAddress,
                                                      Collection<String> idIn,
                                                      String serverType) {
        return selectPage(page, Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(serverType), ResourceServerDO::getServerType, serverType)
                .eq(StrUtil.isNotBlank(lifecycleState), ResourceServerDO::getLifecycleState, lifecycleState)
                .eq(StrUtil.isNotBlank(region), ResourceServerDO::getRegion, region)
                .like(StrUtil.isNotBlank(name), ResourceServerDO::getName, name)
                .like(StrUtil.isNotBlank(ipAddress), ResourceServerDO::getIpAddress, ipAddress)
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
