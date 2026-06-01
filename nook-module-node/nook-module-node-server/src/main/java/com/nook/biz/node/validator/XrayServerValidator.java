package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.dal.mysql.mapper.XrayClientMapper;
import com.nook.biz.node.service.xray.config.XrayConfigService;
import com.nook.biz.node.service.xray.server.XrayServerService;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Xray 实例 + 配置业务校验
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class XrayServerValidator {

    private final XrayServerService xrayServerService;
    private final XrayConfigService xrayConfigService;
    private final XrayClientMapper xrayClientMapper;

    /**
     * 校验 xray 实例存在; 不存在抛 SERVER_STATE_NOT_FOUND.
     *
     * @param serverId resource_server.id
     * @return XrayServerDO
     */
    public XrayServerDO validateExists(String serverId) {
        XrayServerDO row = xrayServerService.get(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(XrayErrorCode.SERVER_STATE_NOT_FOUND, serverId);
        }
        return row;
    }

    /**
     * 装机入参跨字段校验: useTls=true 时 domain / tls 路径必填
     *
     * @param reqVO 装机入参
     */
    public void validateInstallReq(XrayServerInstallReqVO reqVO) {
        if (!Boolean.TRUE.equals(reqVO.getUseTls())) return;
        if (StrUtil.isBlank(reqVO.getDomain())) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "useTls=true 时 domain 必填");
        }
        if (StrUtil.isBlank(reqVO.getTlsCertPath()) || StrUtil.isBlank(reqVO.getTlsKeyPath())) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "useTls=true 时 tlsCertPath / tlsKeyPath 必填");
        }
    }

    /**
     * 重装前置校验: 跟现有活客户冲突的参数变更直接拒绝.
     * 有活客户且客户面参数 (sharedInboundPort / wsPath / domain 等) 变了抛 NODE_PARAM_CHANGE_BLOCKED;
     * 首次部署或活客户数为 0 都跳过.
     *
     * @param serverId 服务器编号
     * @param reqVO    装机入参
     */
    public void validateAgainstActiveClients(String serverId, XrayServerInstallReqVO reqVO) {
        long activeCount = xrayClientMapper.selectCount(Wrappers.<XrayClientDO>lambdaQuery()
                .eq(XrayClientDO::getServerId, serverId));

        XrayServerDO existingServer = xrayServerService.get(serverId);
        if (existingServer == null || activeCount == 0) return;

        XrayConfigDO existingConfig = xrayConfigService.get(serverId);
        if (existingConfig == null) return;

        List<String> mismatches = new ArrayList<>();
        if (!Objects.equals(existingConfig.getSharedInboundPort(), reqVO.getSharedInboundPort())) {
            mismatches.add("sharedInboundPort: " + existingConfig.getSharedInboundPort()
                    + " → " + reqVO.getSharedInboundPort());
        }
        if (!Objects.equals(existingConfig.getProtocol(), reqVO.getProtocol())) {
            mismatches.add("protocol: " + existingConfig.getProtocol() + " → " + reqVO.getProtocol());
        }
        if (!Objects.equals(existingConfig.getTransport(), reqVO.getTransport())) {
            mismatches.add("transport: " + existingConfig.getTransport() + " → " + reqVO.getTransport());
        }
        if (!Objects.equals(existingConfig.getListenIp(), reqVO.getListenIp())) {
            mismatches.add("listenIp: " + existingConfig.getListenIp() + " → " + reqVO.getListenIp());
        }
        if (!Objects.equals(existingConfig.getWsPath(), reqVO.getWsPath())) {
            mismatches.add("wsPath: " + existingConfig.getWsPath() + " → " + reqVO.getWsPath());
        }
        // useTls=false 时 domain 应落库为 null; useTls=true 时落 reqVO.domain
        String newDomain = Boolean.TRUE.equals(reqVO.getUseTls()) ? reqVO.getDomain() : null;
        if (!Objects.equals(existingConfig.getDomain(), newDomain)) {
            mismatches.add("domain: " + existingConfig.getDomain() + " → " + newDomain);
        }
        if (!mismatches.isEmpty()) {
            throw new BusinessException(XrayErrorCode.NODE_PARAM_CHANGE_BLOCKED,
                    serverId, activeCount, String.join("; ", mismatches));
        }
    }
}
