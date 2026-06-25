package com.nook.biz.node.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingListItemRespVO;
import com.nook.biz.node.entity.ResourceServerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    default boolean existsByIpAddress(String ipAddress) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getIpAddress, ipAddress));
    }

    default boolean existsByIpAddressExcludingId(String ipAddress, String excludeId) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getIpAddress, ipAddress)
                .ne(ResourceServerDO::getId, excludeId));
    }

    default List<ResourceServerDO> selectByServerType(String serverType) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, serverType));
    }

    default List<ResourceServerDO> selectLiveLandingsByRegion(String region) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.LANDING.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState())
                .eq(ResourceServerDO::getRegion, region));
    }

    default List<ResourceServerDO> selectLiveLandingsByRegions(Collection<String> regions) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.LANDING.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState())
                .in(ResourceServerDO::getRegion, regions));
    }

    default List<ResourceServerDO> selectLiveFrontlines() {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.FRONTLINE.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState()));
    }

    default List<ResourceServerDO> selectLiveFrontlinesByRegion(String region) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.FRONTLINE.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState())
                .eq(ResourceServerDO::getRegion, region));
    }

    default List<ResourceServerDO> selectByLifecycleState(String lifecycleState) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getLifecycleState, lifecycleState));
    }

    default Map<String, Long> countGroupByRegion() {
        List<Map<String, Object>> rows = selectMaps(Wrappers.<ResourceServerDO>query()
                .select("region", "COUNT(*) AS cnt")
                .isNotNull("region")
                .ne("region", "")
                .groupBy("region"));
        Map<String, Long> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("region"), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    IPage<ServerLandingListItemRespVO> selectLandingPage(IPage<ServerLandingListItemRespVO> page,
            @Param("keyword") String keyword, @Param("lifecycleState") String lifecycleState,
            @Param("ipTypeId") String ipTypeId,
            @Param("regionCodes") Collection<String> regionCodes, @Param("serverType") String serverType);

    IPage<ServerFrontlineListItemRespVO> selectFrontlinePage(IPage<ServerFrontlineListItemRespVO> page,
            @Param("name") String name, @Param("host") String host, @Param("lifecycleState") String lifecycleState,
            @Param("regionCodes") Collection<String> regionCodes, @Param("serverType") String serverType);

    ServerFrontlineListItemRespVO selectServerRuntimeDetail(@Param("serverId") String serverId);

    default void updateLifecycleState(String id, String lifecycleState) {
        update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getLifecycleState, lifecycleState)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getId, id));
    }

    default int migrateRegion(String oldRegion, String newRegion) {
        return update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getRegion, newRegion)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getRegion, oldRegion));
    }
}
