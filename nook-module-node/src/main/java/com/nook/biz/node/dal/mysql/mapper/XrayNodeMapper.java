package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.xray.vo.XrayNodePageReqVO;
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

    /** 分页; serverId 精确匹配, xrayVersion 模糊匹配, 按 installed_at 倒序 */
    default IPage<XrayNodeDO> selectPageByQuery(IPage<XrayNodeDO> page, XrayNodePageReqVO reqVO) {
        return selectPage(page, Wrappers.<XrayNodeDO>lambdaQuery()
                .eq(StrUtil.isNotBlank(reqVO.getServerId()), XrayNodeDO::getServerId, reqVO.getServerId())
                .like(StrUtil.isNotBlank(reqVO.getXrayVersion()), XrayNodeDO::getXrayVersion, reqVO.getXrayVersion())
                .orderByDesc(XrayNodeDO::getInstalledAt));
    }
}
