package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.node.api.xray.dto.XrayNodeRespDTO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Xray 节点 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class XrayNodeApiImpl implements XrayNodeApi {

    private final XrayNodeMapper xrayNodeMapper;

    @Override
    public XrayNodeRespDTO getByServerId(String serverId) {
        XrayNodeDO row = xrayNodeMapper.selectById(serverId);
        if (row == null) return null;
        return toDto(row);
    }

    @Override
    public Map<String, XrayNodeRespDTO> listByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) return Collections.emptyMap();
        return xrayNodeMapper.selectBatchIds(serverIds).stream()
                .collect(Collectors.toMap(XrayNodeDO::getServerId, XrayNodeApiImpl::toDto, (a, b) -> a));
    }

    /** 只 copy 暴露字段, 内部 wsPath / domain / TLS 等不外发. */
    private static XrayNodeRespDTO toDto(XrayNodeDO row) {
        XrayNodeRespDTO dto = new XrayNodeRespDTO();
        dto.setServerId(row.getServerId());
        dto.setXrayBinaryPath(row.getXrayBinaryPath());
        dto.setXrayApiPort(row.getXrayApiPort());
        dto.setXrayVersion(row.getXrayVersion());
        return dto;
    }
}
