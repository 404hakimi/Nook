package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolInstallDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * SOCKS5 装机事实 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceIpPoolInstallMapper extends BaseMapper<ResourceIpPoolInstallDO> {

    /**
     * 批量按 ipId 查; convert 层 enrich 用.
     *
     * @param ipIds ipId 集合; 空集合返空 list
     * @return 装机事实列表
     */
    default List<ResourceIpPoolInstallDO> selectByIpIds(Collection<String> ipIds) {
        if (ipIds == null || ipIds.isEmpty()) return List.of();
        return selectList(Wrappers.<ResourceIpPoolInstallDO>lambdaQuery()
                .in(ResourceIpPoolInstallDO::getIpId, ipIds));
    }

    /**
     * 更新 last_dante_uptime; replay / health probe 探测到 dante 启动后回写.
     *
     * @param ipId   IP 池编号
     * @param uptime 探测到的 dante 启动时间
     * @return 受影响行数
     */
    default int updateLastDanteUptime(String ipId, LocalDateTime uptime) {
        return update(null, Wrappers.<ResourceIpPoolInstallDO>lambdaUpdate()
                .set(ResourceIpPoolInstallDO::getLastDanteUptime, uptime)
                .set(ResourceIpPoolInstallDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceIpPoolInstallDO::getIpId, ipId));
    }
}
