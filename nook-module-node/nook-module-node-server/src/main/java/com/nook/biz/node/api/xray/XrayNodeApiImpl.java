package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayNodeRespDTO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** node-api {@link XrayNodeApi} 实现. */
@Service
@RequiredArgsConstructor
public class XrayNodeApiImpl implements XrayNodeApi {

    private final XrayNodeMapper xrayNodeMapper;

    @Override
    public XrayNodeRespDTO getByServerId(String serverId) {
        XrayNodeDO row = xrayNodeMapper.selectById(serverId);
        if (row == null) return null;
        // 只 copy 暴露字段, 内部 wsPath / domain / TLS 等不外发
        XrayNodeRespDTO dto = new XrayNodeRespDTO();
        dto.setServerId(row.getServerId());
        dto.setXrayBinaryPath(row.getXrayBinaryPath());
        dto.setXrayApiPort(row.getXrayApiPort());
        return dto;
    }
}
