package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Xray 节点业务校验.
 *
 * @author nook
 */
@Component
public class XrayNodeValidator {

    @Resource
    private XrayNodeMapper xrayNodeMapper;
    @Resource
    private XrayClientMapper xrayClientMapper;

    /**
     * 校验 xray 节点存在; 不存在抛 SERVER_STATE_NOT_FOUND (语义=该 server 还没通过 nook 部署 xray).
     *
     * @param serverId resource_server.id
     * @return XrayNodeDO
     */
    public XrayNodeDO validateExists(String serverId) {
        XrayNodeDO row = xrayNodeMapper.selectById(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(XrayErrorCode.SERVER_STATE_NOT_FOUND, serverId);
        }
        return row;
    }

    /**
     * 安装入参跨字段校验: useTls=true 时 domain / tls 路径必填. 跟 jakarta annotation 互补.
     *
     * @param reqVO 安装入参
     */
    public void validateInstallReq(XrayServerInstallReqVO reqVO) {
        if (!Boolean.TRUE.equals(reqVO.getUseTls())) return;
        if (StrUtil.isBlank(reqVO.getDomain())) {
            throw new IllegalArgumentException("useTls=true 时 domain 必填");
        }
        if (StrUtil.isBlank(reqVO.getTlsCertPath()) || StrUtil.isBlank(reqVO.getTlsKeyPath())) {
            throw new IllegalArgumentException("useTls=true 时 tlsCertPath / tlsKeyPath 必填");
        }
    }

    /**
     * 重装前置校验: 跟现有活客户冲突的参数变更直接拒绝.
     *
     * <p>两类拒绝:
     * <ul>
     *   <li>touchdownSize 缩到比现有客户数还小 → 抛 TOUCHDOWN_SHRINK_BLOCKED</li>
     *   <li>有活客户 + 客户面参数 (sharedInboundPort / wsPath / domain) 变了 → 抛 NODE_PARAM_CHANGE_BLOCKED;
     *       因为这 3 个字段决定客户端 vmess URL, 变了所有现有 URL 失效</li>
     * </ul>
     * 首次部署 (xray_node 不存在) / 活客户数为 0 都跳过, 不影响 dev 期反复重装.
     *
     * @param serverId resource_server.id
     * @param reqVO    安装入参
     */
    public void validateAgainstActiveClients(String serverId, XrayServerInstallReqVO reqVO) {
        long activeCount = xrayClientMapper.selectCount(Wrappers.<XrayClientDO>lambdaQuery()
                .eq(XrayClientDO::getServerId, serverId));
        if (activeCount > reqVO.getTouchdownSize()) {
            throw new BusinessException(XrayErrorCode.TOUCHDOWN_SHRINK_BLOCKED,
                    serverId, activeCount, reqVO.getTouchdownSize());
        }

        XrayNodeDO existing = xrayNodeMapper.selectById(serverId);
        if (existing == null || activeCount == 0) return;

        List<String> mismatches = new ArrayList<>();
        if (!Objects.equals(existing.getSharedInboundPort(), reqVO.getSharedInboundPort())) {
            mismatches.add("sharedInboundPort: " + existing.getSharedInboundPort()
                    + " → " + reqVO.getSharedInboundPort());
        }
        if (!Objects.equals(existing.getProtocol(), reqVO.getProtocol())) {
            mismatches.add("protocol: " + existing.getProtocol() + " → " + reqVO.getProtocol());
        }
        if (!Objects.equals(existing.getTransport(), reqVO.getTransport())) {
            mismatches.add("transport: " + existing.getTransport() + " → " + reqVO.getTransport());
        }
        if (!Objects.equals(existing.getListenIp(), reqVO.getListenIp())) {
            mismatches.add("listenIp: " + existing.getListenIp() + " → " + reqVO.getListenIp());
        }
        if (!Objects.equals(existing.getWsPath(), reqVO.getWsPath())) {
            mismatches.add("wsPath: " + existing.getWsPath() + " → " + reqVO.getWsPath());
        }
        // useTls=false 时 domain 应落库为 null; useTls=true 时落 reqVO.domain. 跟 upsertXrayNode 实际逻辑对齐.
        String newDomain = Boolean.TRUE.equals(reqVO.getUseTls()) ? reqVO.getDomain() : null;
        if (!Objects.equals(existing.getDomain(), newDomain)) {
            mismatches.add("domain: " + existing.getDomain() + " → " + newDomain);
        }
        if (!mismatches.isEmpty()) {
            throw new BusinessException(XrayErrorCode.NODE_PARAM_CHANGE_BLOCKED,
                    serverId, activeCount, String.join("; ", mismatches));
        }
    }
}
