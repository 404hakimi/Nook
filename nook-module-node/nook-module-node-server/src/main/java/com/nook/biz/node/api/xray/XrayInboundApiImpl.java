package com.nook.biz.node.api.xray;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.nook.biz.node.api.xray.dto.XrayInboundDTO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.framework.xray.inbound.config.InboundParams;
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
 * Xray 入站 Api 实现类
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
            // 解析协议语义参数 (vmess ws path / reality 客户端参数从此取)
            InboundParams params = StrUtil.isBlank(cfg.getParams())
                    ? null : JSON.parseObject(cfg.getParams(), InboundParams.class);
            result.put(entry.getKey(), XrayInboundApiConvert.INSTANCE.toInboundDTO(cfg, host, params));
        }
        return result;
    }
}
