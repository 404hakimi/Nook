package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.xray.dto.XrayServerRespDTO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.mysql.mapper.XrayServerMapper;
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
public class XrayServerApiImpl implements XrayServerApi {

    @Resource
    private XrayServerMapper xrayServerMapper;

    @Override
    public XrayServerRespDTO getByServerId(String serverId) {
        XrayServerDO row = xrayServerMapper.selectById(serverId);
        return ObjectUtil.isNull(row) ? null : toDto(row);
    }

    @Override
    public Map<String, XrayServerRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                xrayServerMapper.selectBatchIds(serverIds), XrayServerDO::getServerId, XrayServerApiImpl::toDto);
    }

    /** Api 仅暴露 binary / apiPort / version (跨模块刚需); inbound 配置等模块私有, 不外发 */
    private static XrayServerRespDTO toDto(XrayServerDO row) {
        XrayServerRespDTO dto = new XrayServerRespDTO();
        dto.setServerId(row.getServerId());
        dto.setXrayBinaryPath(row.getXrayBinaryPath());
        dto.setXrayApiPort(row.getXrayApiPort());
        dto.setXrayVersion(row.getXrayVersion());
        return dto;
    }
}
