package com.nook.biz.node.api.xray;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.XrayInstallStatusEnum;
import com.nook.biz.node.api.xray.dto.XrayInstallRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.mapper.XrayInstallMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Xray 实例 API
 *
 * @author nook
 */
@Service
public class XrayInstallApiImpl implements XrayInstallApi {

    @Resource
    private XrayInstallMapper xrayInstallMapper;

    @Override
    public XrayInstallRespDTO getXrayInstall(String serverId) {
        XrayInstallDO bean = xrayInstallMapper.selectById(serverId);
        return ObjectUtil.isNull(bean) ? null : XrayInstallApiConvert.INSTANCE.toRespDTO(bean);
    }

    @Override
    public boolean isDomainBound(String domainId) {
        return xrayInstallMapper.selectCount(new LambdaQueryWrapper<XrayInstallDO>()
                .eq(XrayInstallDO::getDomainId, domainId)) > 0;
    }

    @Override
    public void markDeployedIfDeploying(String serverId) {
        // 原子条件更新: 仅 deploying → ok (+ installedAt); 已 ok/failed 不动 (不回退终态)
        xrayInstallMapper.update(null, new LambdaUpdateWrapper<XrayInstallDO>()
                .eq(XrayInstallDO::getServerId, serverId)
                .eq(XrayInstallDO::getInstallStatus, XrayInstallStatusEnum.DEPLOYING.getCode())
                .set(XrayInstallDO::getInstallStatus, XrayInstallStatusEnum.OK.getCode())
                .set(XrayInstallDO::getInstalledAt, LocalDateTime.now()));
    }
}
