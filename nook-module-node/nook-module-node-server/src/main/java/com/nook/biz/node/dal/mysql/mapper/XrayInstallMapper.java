package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.node.XrayInstallDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * Xray 实例元数据 Mapper
 *
 * @author nook
 */
@Mapper
public interface XrayInstallMapper extends BaseMapper<XrayInstallDO> {

    /** 更新 last_xray_uptime, replay 完成后打点; 显式 set updated_at 因 wrapper 更新不走 MetaObjectHandler 自动 fill. */
    default int updateXrayUptime(String serverId, LocalDateTime uptime) {
        return update(null, Wrappers.<XrayInstallDO>lambdaUpdate()
                .set(XrayInstallDO::getLastXrayUptime, uptime)
                .set(XrayInstallDO::getUpdatedAt, LocalDateTime.now())
                .eq(XrayInstallDO::getServerId, serverId));
    }
}
