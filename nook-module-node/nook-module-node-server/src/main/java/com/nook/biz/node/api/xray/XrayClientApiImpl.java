package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.xray.dto.XrayClientNodeDTO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayConfigMapper;
import com.nook.biz.trade.api.SubscriptionCertApi;
import com.nook.biz.trade.api.dto.SubscriptionCertRespDTO;
import com.nook.biz.trade.api.enums.TradeCertStatusEnum;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link XrayClientApi} 实现; 从订阅凭证 + xray 共享配置 + 服务器拼连接信息.
 *
 * @author nook
 */
@Service
public class XrayClientApiImpl implements XrayClientApi {

    @Resource
    private SubscriptionCertApi subscriptionCertApi;
    @Resource
    private XrayConfigMapper xrayConfigMapper;
    @Resource
    private ResourceServerMapper resourceServerMapper;

    @Override
    public List<XrayClientNodeDTO> getNodeInfos(Collection<String> clientIds) {
        if (CollUtil.isEmpty(clientIds)) {
            return List.of();
        }
        // 仅应运行且已分配线路机的凭证才拼连接信息
        List<SubscriptionCertRespDTO> certs = new ArrayList<>();
        for (SubscriptionCertRespDTO cert : subscriptionCertApi.listByIds(clientIds)) {
            if (TradeCertStatusEnum.ACTIVE.matches(cert.getCertStatus()) && ObjectUtil.isNotNull(cert.getServerId())) {
                certs.add(cert);
            }
        }
        if (CollUtil.isEmpty(certs)) {
            return List.of();
        }
        Set<String> serverIds = CollectionUtils.convertSet(certs, SubscriptionCertRespDTO::getServerId);
        Map<String, XrayConfigDO> cfgMap = CollectionUtils.convertMap(
                xrayConfigMapper.selectBatchIds(serverIds), XrayConfigDO::getServerId);
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);

        List<XrayClientNodeDTO> out = new ArrayList<>(certs.size());
        for (SubscriptionCertRespDTO cert : certs) {
            XrayConfigDO cfg = cfgMap.get(cert.getServerId());
            if (ObjectUtil.isNull(cfg)) {
                continue;
            }
            ResourceServerDO srv = serverMap.get(cert.getServerId());
            // host 优先用线路机域名, 否则回退出网 IP
            String host = StrUtil.isNotBlank(cfg.getDomain())
                    ? cfg.getDomain()
                    : (ObjectUtil.isNull(srv) ? null : srv.getIpAddress());
            if (StrUtil.isBlank(host)) {
                continue;
            }
            XrayClientNodeDTO dto = new XrayClientNodeDTO();
            dto.setClientId(cert.getId());
            dto.setClientUuid(cert.getAuthSecret());
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
}
