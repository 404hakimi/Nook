package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.xray.dto.XrayInstallRespDTO;
import com.nook.biz.node.convert.xray.XrayInstallConvert;
import com.nook.biz.node.dal.dataobject.node.XrayInstallDO;
import com.nook.biz.node.dal.mysql.mapper.XrayInstallMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * Xray 实例 Api 实现类
 *
 * @author nook
 */
@Service
public class XrayInstallApiImpl implements XrayInstallApi {

    @Resource
    private XrayInstallMapper xrayInstallMapper;

    @Override
    public XrayInstallRespDTO getByServerId(String serverId) {
        XrayInstallDO row = xrayInstallMapper.selectById(serverId);
        return ObjectUtil.isNull(row) ? null : XrayInstallConvert.INSTANCE.toRespDTO(row);
    }

    @Override
    public Map<String, XrayInstallRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                xrayInstallMapper.selectBatchIds(serverIds), XrayInstallDO::getServerId,
                XrayInstallConvert.INSTANCE::toRespDTO);
    }
}
