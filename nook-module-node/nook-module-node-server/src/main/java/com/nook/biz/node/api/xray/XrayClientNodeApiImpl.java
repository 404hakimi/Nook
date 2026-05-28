package com.nook.biz.node.api.xray;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.XrayClientStatusEnum;
import com.nook.biz.node.api.xray.dto.XrayClientNodeDTO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link XrayClientNodeApi} 实现; 组合 xray_client + xray_config + resource_server 拼连接信息.
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class XrayClientNodeApiImpl implements XrayClientNodeApi {

    private final XrayClientMapper clientMapper;
    private final XrayConfigMapper configMapper;
    private final ResourceServerMapper serverMapper;

    @Override
    public List<XrayClientNodeDTO> getNodeInfos(Collection<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<XrayClientDO> clients = clientMapper.selectBatchIds(clientIds).stream()
                .filter(c -> XrayClientStatusEnum.RUNNING.matches(c.getStatus()))
                .collect(Collectors.toList());
        if (clients.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> serverIds = clients.stream()
                .map(XrayClientDO::getServerId).collect(Collectors.toSet());
        Map<String, XrayConfigDO> cfgMap = configMapper.selectBatchIds(serverIds).stream()
                .collect(Collectors.toMap(XrayConfigDO::getServerId, Function.identity()));
        Map<String, ResourceServerDO> serverMap = serverMapper.selectBatchIds(serverIds).stream()
                .collect(Collectors.toMap(ResourceServerDO::getId, Function.identity()));

        List<XrayClientNodeDTO> out = new ArrayList<>(clients.size());
        for (XrayClientDO c : clients) {
            XrayConfigDO cfg = cfgMap.get(c.getServerId());
            if (cfg == null) {
                continue;
            }
            ResourceServerDO srv = serverMap.get(c.getServerId());
            String host = StrUtil.isNotBlank(cfg.getDomain())
                    ? cfg.getDomain()
                    : (srv != null ? srv.getIpAddress() : null);
            if (StrUtil.isBlank(host)) {
                continue;
            }
            XrayClientNodeDTO dto = new XrayClientNodeDTO();
            dto.setClientId(c.getId());
            dto.setClientUuid(c.getClientUuid());
            dto.setHost(host);
            dto.setPort(cfg.getSharedInboundPort());
            dto.setProtocol(cfg.getProtocol());
            dto.setTransport(cfg.getTransport());
            dto.setWsPath(cfg.getWsPath());
            dto.setTls(StrUtil.isNotBlank(cfg.getTlsCertPath()));
            out.add(dto);
        }
        return out;
    }

    @Override
    public Map<String, String> getServerIdByClientIds(Collection<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return clientMapper.selectBatchIds(clientIds).stream()
                .filter(c -> Objects.nonNull(c.getServerId()))
                .collect(Collectors.toMap(XrayClientDO::getId, XrayClientDO::getServerId, (a, b) -> a));
    }
}
