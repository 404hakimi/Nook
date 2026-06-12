package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.xray.dto.XrayInboundDTO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.node.mapper.XrayInboundMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Xray入站 API
 *
 * @author nook
 */
@Service
public class XrayInboundApiImpl implements XrayInboundApi {

    @Resource
    private XrayInboundMapper xrayInboundMapper;
    @Resource
    private ResourceServerMapper resourceServerMapper;

    @Override
    public Map<String, XrayInboundDTO> listInboundByServerIds(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return Map.of();
        }
        Map<String, XrayInboundDO> cfgMap = CollectionUtils.convertMap(
                xrayInboundMapper.selectBatchIds(serverIds), XrayInboundDO::getServerId);
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);
        Map<String, XrayInboundDTO> result = new HashMap<>(cfgMap.size());
        for (Map.Entry<String, XrayInboundDO> entry : cfgMap.entrySet()) {
            XrayInboundDO cfg = entry.getValue();
            ResourceServerDO srv = serverMap.get(entry.getKey());
            // host 优先线路机域名, 否则回退出网 IP; 都没有则拼不出连接, 跳过该线路机
            String host = StrUtil.isNotBlank(cfg.getDomain())
                    ? cfg.getDomain()
                    : (ObjectUtil.isNull(srv) ? null : srv.getIpAddress());
            if (StrUtil.isBlank(host)) {
                continue;
            }
            XrayInboundDTO dto = new XrayInboundDTO();
            dto.setHost(host);
            dto.setPort(cfg.getSharedInboundPort());
            dto.setProtocol(cfg.getProtocol());
            dto.setTransport(cfg.getTransport());
            dto.setWsPath(cfg.getWsPath());
            dto.setTls(StrUtil.isNotBlank(cfg.getTlsCertPath()));
            result.put(entry.getKey(), dto);
        }
        return result;
    }
}
