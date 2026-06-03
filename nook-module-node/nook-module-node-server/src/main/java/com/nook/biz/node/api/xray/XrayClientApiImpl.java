package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.XrayClientStatusEnum;
import com.nook.biz.node.api.xray.dto.XrayClientNodeDTO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayConfigMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link XrayClientApi} 实现; 组合 xray_client + xray_config + resource_server 拼连接信息.
 *
 * @author nook
 */
@Service
public class XrayClientApiImpl implements XrayClientApi {

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private XrayConfigMapper xrayConfigMapper;
    @Resource
    private ResourceServerMapper resourceServerMapper;

    @Override
    public List<XrayClientNodeDTO> getNodeInfos(Collection<String> clientIds) {
        if (CollUtil.isEmpty(clientIds)) {
            return List.of();
        }
        List<XrayClientDO> clients = xrayClientMapper.selectBatchIds(clientIds).stream()
                .filter(c -> XrayClientStatusEnum.RUNNING.matches(c.getStatus()))
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(clients)) {
            return List.of();
        }
        Set<String> serverIds = CollectionUtils.convertSet(clients, XrayClientDO::getServerId);
        Map<String, XrayConfigDO> cfgMap = CollectionUtils.convertMap(
                xrayConfigMapper.selectBatchIds(serverIds), XrayConfigDO::getServerId);
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);

        List<XrayClientNodeDTO> out = new ArrayList<>(clients.size());
        for (XrayClientDO c : clients) {
            XrayConfigDO cfg = cfgMap.get(c.getServerId());
            if (ObjectUtil.isNull(cfg)) {
                continue;
            }
            ResourceServerDO srv = serverMap.get(c.getServerId());
            String host = StrUtil.isNotBlank(cfg.getDomain())
                    ? cfg.getDomain()
                    : (ObjectUtil.isNull(srv) ? null : srv.getIpAddress());
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
        if (CollUtil.isEmpty(clientIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                xrayClientMapper.selectBatchIds(clientIds), XrayClientDO::getId, XrayClientDO::getServerId);
    }

    @Override
    public Map<String, String> getLandingIdByClientIds(Collection<String> clientIds) {
        if (CollUtil.isEmpty(clientIds)) {
            return Map.of();
        }
        // ip_id 为 NOT NULL, 直接转 clientId → ip_id
        return CollectionUtils.convertMap(
                xrayClientMapper.selectBatchIds(clientIds), XrayClientDO::getId, XrayClientDO::getIpId);
    }

    @Override
    public String getClientIdByLandingId(String landingServerId) {
        if (StrUtil.isBlank(landingServerId)) {
            return null;
        }
        XrayClientDO c = xrayClientMapper.selectByIpId(landingServerId);
        return ObjectUtil.isNull(c) ? null : c.getId();
    }

    @Override
    public Map<String, String> getClientServerMapByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return Map.of();
        }
        return CollectionUtils.convertMap(
                xrayClientMapper.selectByServerIds(serverIds), XrayClientDO::getId, XrayClientDO::getServerId);
    }
}
