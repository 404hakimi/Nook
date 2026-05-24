package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.node.api.xray.dto.XrayServerRespDTO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.mysql.mapper.XrayServerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Xray 实例 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class XrayServerApiImpl implements XrayServerApi {

    private final XrayServerMapper xrayServerMapper;

    @Override
    public XrayServerRespDTO getByServerId(String serverId) {
        XrayServerDO row = xrayServerMapper.selectById(serverId);
        if (row == null) return null;
        return toDto(row);
    }

    @Override
    public Map<String, XrayServerRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Collections.emptyMap();
        return xrayServerMapper.selectBatchIds(serverIds).stream()
                .collect(Collectors.toMap(XrayServerDO::getServerId, XrayServerApiImpl::toDto, (a, b) -> a));
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
