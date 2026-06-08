package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.controller.resource.vo.ServerFrontlineListItemRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
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

    /** 区域内运行中落地机 (选址第一步, 区域过滤把范围缩到一个机房). */
    default List<ResourceServerDO> selectLiveLandingsByRegion(String region) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.LANDING.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState())
                .eq(ResourceServerDO::getRegion, region));
    }

    /** 多区域内运行中落地机 (批量算容量用; 一次覆盖一批套餐涉及的所有区域). */
    default List<ResourceServerDO> selectLiveLandingsByRegions(Collection<String> regions) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.LANDING.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState())
                .in(ResourceServerDO::getRegion, regions));
    }

    /** 全部运行中线路机 (故障切换巡检用). */
    default List<ResourceServerDO> selectLiveFrontlines() {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.FRONTLINE.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState()));
    }

    /** 区域内运行中线路机 (选线路机用). */
    default List<ResourceServerDO> selectLiveFrontlinesByRegion(String region) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getServerType, ResourceServerTypeEnum.FRONTLINE.getState())
                .eq(ResourceServerDO::getLifecycleState, ResourceServerLifecycleEnum.LIVE.getState())
                .eq(ResourceServerDO::getRegion, region));
    }

    /** 按生命周期状态查 server (线路机 + 落地机); 状态值走 {@link ResourceServerLifecycleEnum}. */
    default List<ResourceServerDO> selectByLifecycleState(String lifecycleState) {
        return selectList(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getLifecycleState, lifecycleState));
    }

    /**
     * 列表分页. ipAddress 直接 LIKE 主表; idIn (来自子表预过滤如 landing.status) 可选作 id 集合过滤;
     * serverType (frontline / landing) 可选.
     */
    default IPage<ResourceServerDO> selectPageByQuery(IPage<ResourceServerDO> page, String name,
                                                      String lifecycleState, Collection<String> regionCodes,
                                                      String ipAddress,
                                                      Collection<String> idIn,
                                                      String serverType) {
        return selectPage(page, Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(serverType), ResourceServerDO::getServerType, serverType)
                .eq(StrUtil.isNotBlank(lifecycleState), ResourceServerDO::getLifecycleState, lifecycleState)
                .in(CollUtil.isNotEmpty(regionCodes), ResourceServerDO::getRegion, regionCodes)
                .like(StrUtil.isNotBlank(name), ResourceServerDO::getName, name)
                .like(StrUtil.isNotBlank(ipAddress), ResourceServerDO::getIpAddress, ipAddress)
                .in(idIn != null, ResourceServerDO::getId, idIn)
                .orderByDesc(ResourceServerDO::getCreatedAt));
    }

    /** 按区域统计机器数 (线路机+落地机, 即全部 resource_server 行); 返回 区域码 → 机器数. */
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

    /** 区域码迁移: 旧码机器改挂新码 (区域更正级联用); Wrapper 更新须显式 set updated_at. */
    default int migrateRegion(String oldRegion, String newRegion) {
        return update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getRegion, newRegion)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getRegion, oldRegion));
    }

    /**
     * 落地机分页, 按账单到期升序 (空排末) → 创建倒序
     *
     * @param page           分页参数
     * @param name           名称模糊
     * @param lifecycleState 生命周期
     * @param regionCodes    区域码集合
     * @param idIn           子表预过滤的 id 集合 (status / ipType)
     * @param serverType     机器类型
     * @return 主表分页
     */
    IPage<ResourceServerDO> selectLandingPageOrderByExpiry(IPage<ResourceServerDO> page,
            @Param("name") String name, @Param("lifecycleState") String lifecycleState,
            @Param("regionCodes") Collection<String> regionCodes, @Param("idIn") Collection<String> idIn,
            @Param("serverType") String serverType);

    /**
     * 线路机分页: 主表 LEFT JOIN 运行时 / 配额 / 当周期流量 / xray, 直接出列表项视图
     *
     * @param page           分页参数
     * @param name           名称模糊
     * @param host           IP 模糊
     * @param lifecycleState 生命周期
     * @param regionCodes    区域码集合
     * @param serverType     机器类型
     * @return 列表项视图分页
     */
    IPage<ServerFrontlineListItemRespVO> selectFrontlinePage(IPage<ServerFrontlineListItemRespVO> page,
            @Param("name") String name, @Param("host") String host, @Param("lifecycleState") String lifecycleState,
            @Param("regionCodes") Collection<String> regionCodes, @Param("serverType") String serverType);

    /**
     * 服务器运行时详情视图 (线路机 / 落地机共用): 单条同款 JOIN
     *
     * @param serverId 服务器编号
     * @return 运行时详情视图 (不存在返 null)
     */
    ServerFrontlineListItemRespVO selectServerRuntimeDetail(@Param("serverId") String serverId);

    /** 切换生命周期; Wrapper 更新须显式 set updated_at. */
    default int updateLifecycleState(String id, String newState) {
        return update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getLifecycleState, newState)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getId, id));
    }
}
