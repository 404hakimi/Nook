package com.nook.biz.node.enricher;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.resource.entity.ResourceServer;
import com.nook.biz.node.resource.mapper.ResourceServerMapper;
import com.nook.biz.operation.api.OpLogEnricher;
import com.nook.biz.operation.controller.vo.OpLogRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 给 op_log VO 补 serverName (resource_server.name) 和 targetName (xray_client.client_email).
 *
 * <p>批量 IN 查避免 N+1; targetId 不一定是 xray_client.id (server 级 op 没 target), 查不到就静默跳过.
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class NodeOpLogEnricher implements OpLogEnricher {

    private final ResourceServerMapper resourceServerMapper;
    private final XrayClientMapper xrayClientMapper;

    @Override
    public void enrich(List<OpLogRespVO> vos) {
        // 收集需要查的 id 集
        Set<String> serverIds = new HashSet<>();
        Set<String> targetIds = new HashSet<>();
        for (OpLogRespVO vo : vos) {
            if (StrUtil.isNotBlank(vo.getServerId()) && StrUtil.isBlank(vo.getServerName())) {
                serverIds.add(vo.getServerId());
            }
            if (StrUtil.isNotBlank(vo.getTargetId()) && StrUtil.isBlank(vo.getTargetName())) {
                targetIds.add(vo.getTargetId());
            }
        }

        Map<String, String> serverNameMap = new HashMap<>(serverIds.size() * 2);
        if (!serverIds.isEmpty()) {
            List<ResourceServer> servers = resourceServerMapper.selectList(
                    Wrappers.<ResourceServer>lambdaQuery().in(ResourceServer::getId, serverIds));
            for (ResourceServer s : servers) {
                serverNameMap.put(s.getId(), StrUtil.blankToDefault(s.getName(), s.getHost()));
            }
        }

        Map<String, String> targetNameMap = new HashMap<>(targetIds.size() * 2);
        if (!targetIds.isEmpty()) {
            // xray_client 是软删除 (BaseEntity.deleted) 一类 — 走 MP 默认过滤; 删过的 client 这里查不到, 显示原 id
            List<XrayClientDO> clients = xrayClientMapper.selectList(
                    Wrappers.<XrayClientDO>lambdaQuery().in(XrayClientDO::getId, targetIds));
            for (XrayClientDO c : clients) {
                targetNameMap.put(c.getId(), StrUtil.blankToDefault(c.getClientEmail(), c.getId()));
            }
        }

        for (OpLogRespVO vo : vos) {
            if (StrUtil.isBlank(vo.getServerName()) && StrUtil.isNotBlank(vo.getServerId())) {
                vo.setServerName(serverNameMap.getOrDefault(vo.getServerId(), vo.getServerId()));
            }
            if (StrUtil.isBlank(vo.getTargetName()) && StrUtil.isNotBlank(vo.getTargetId())) {
                vo.setTargetName(targetNameMap.getOrDefault(vo.getTargetId(), vo.getTargetId()));
            }
        }
    }
}
