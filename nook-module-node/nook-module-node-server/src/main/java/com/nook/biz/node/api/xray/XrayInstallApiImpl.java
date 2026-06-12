package com.nook.biz.node.api.xray;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.xray.dto.XrayInstallRespDTO;
import com.nook.biz.node.convert.xray.XrayInstallConvert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nook.biz.node.dal.dataobject.node.XrayInstallDO;
import com.nook.biz.node.dal.mysql.mapper.XrayInstallMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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
        return ObjectUtil.isNull(bean) ? null : XrayInstallConvert.INSTANCE.toRespDTO(bean);
    }

    @Override
    public boolean isDomainBound(String domainId) {
        return xrayInstallMapper.selectCount(new LambdaQueryWrapper<XrayInstallDO>()
                .eq(XrayInstallDO::getDomainId, domainId)) > 0;
    }
}
