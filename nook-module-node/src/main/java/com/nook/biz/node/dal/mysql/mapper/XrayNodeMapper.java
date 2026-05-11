package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * Xray 节点 DB 访问层.
 *
 * @author nook
 */
@Mapper
public interface XrayNodeMapper extends BaseMapper<XrayNodeDO> {

    /** 更新 last_xray_uptime, replay 完成后打点. */
    default int updateXrayUptime(String serverId, LocalDateTime uptime) {
        return update(null, Wrappers.<XrayNodeDO>lambdaUpdate()
                .set(XrayNodeDO::getLastXrayUptime, uptime)
                .eq(XrayNodeDO::getServerId, serverId));
    }
}
