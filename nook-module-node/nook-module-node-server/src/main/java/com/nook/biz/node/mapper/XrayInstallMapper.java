package com.nook.biz.node.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.entity.XrayInstallDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * Xray 实例元数据 Mapper
 *
 * @author nook
 */
@Mapper
public interface XrayInstallMapper extends BaseMapper<XrayInstallDO> {

    default int updateXrayUptime(String serverId, LocalDateTime uptime) {
        return update(null, Wrappers.<XrayInstallDO>lambdaUpdate()
                .set(XrayInstallDO::getLastXrayUptime, uptime)
                .set(XrayInstallDO::getUpdatedAt, LocalDateTime.now())
                .eq(XrayInstallDO::getServerId, serverId));
    }
}
