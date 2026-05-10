package com.nook.biz.node.framework.xray.handler;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Xray outbound 增删 CLI 客户端 (走 SSH + `xray api ado/rmo`).
 *
 * <p>同 XrayInboundCliClient 走 CLI 而非纯 gRPC, 原因相同 — AddOutbound 的 protobuf 嵌套
 * 体量大, vendor 不划算; xray CLI 已封装 JSON → OutboundHandlerConfig 的序列化, nook 直接喂 JSON.
 *
 * <p>支持两种 outbound:
 * <ul>
 *   <li>socks: 业务路径, 客户流量从 nook server 转到独享落地 IP</li>
 *   <li>freedom: 直连 (api 通道用 / placeholder 占位用 / revoke 后还原占位用)</li>
 * </ul>
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayOutboundCliClient {

    /** 单条 SSH+CLI 命令默认超时. */
    private static final Duration OP_TIMEOUT = Duration.ofSeconds(30);

    @Resource
    private SshSessionManager sshSessionManager;
    @Resource
    private XrayNodeService xrayNodeService;

    /**
     * 加 socks5 出站 (1:1 模型 provision 时用; 把客户流量打到独享落地 IP).
     *
     * @param serverId  resource_server.id
     * @param tag       outbound tag (1:1 模型: out_slot_XX)
     * @param socksHost 落地 socks5 主机
     * @param socksPort 落地 socks5 端口
     * @param username  socks5 鉴权用户名 (空则无鉴权)
     * @param password  socks5 鉴权密码 (空则无鉴权)
     */
    public void addSocksOutbound(String serverId, String tag,
                                 String socksHost, int socksPort,
                                 String username, String password) {
        XrayNodeDO node = xrayNodeService.loadOrThrow(serverId);
        String json = buildSocksOutboundJson(tag, socksHost, socksPort, username, password);
        execAdo(serverId, node, tag, json);
        log.info("[xray-cli] addSocksOutbound server={} tag={} socks={}:{}",
                serverId, tag, socksHost, socksPort);
    }

    /**
     * 加 freedom 直连出站 (api 通道用 / revoke 后还原 placeholder 占位用).
     *
     * @param serverId resource_server.id
     * @param tag      outbound tag
     */
    public void addFreedomOutbound(String serverId, String tag) {
        XrayNodeDO node = xrayNodeService.loadOrThrow(serverId);
        JSONObject outbound = new JSONObject();
        outbound.put("tag", tag);
        outbound.put("protocol", "freedom");
        execAdo(serverId, node, tag, outbound.toJSONString());
        log.info("[xray-cli] addFreedomOutbound server={} tag={}", serverId, tag);
    }

    /**
     * 删 outbound (按 tag).
     *
     * @param serverId resource_server.id
     * @param tag      outbound tag
     */
    public void removeOutbound(String serverId, String tag) {
        XrayNodeDO node = xrayNodeService.loadOrThrow(serverId);
        SshSession session = sshSessionManager.acquire(serverId);
        String cmd = "xray api rmo --server=127.0.0.1:" + node.getXrayGrpcPort() + " "
                + escapeShellArg(tag);
        try {
            session.ssh().exec(cmd, OP_TIMEOUT);
            log.info("[xray-cli] removeOutbound server={} tag={}", serverId, tag);
        } catch (BusinessException be) {
            throw mapRemoveOutboundError(be, serverId, tag);
        }
    }

    // ===== 私有 =====

    private void execAdo(String serverId, XrayNodeDO node, String tag, String json) {
        SshSession session = sshSessionManager.acquire(serverId);
        String cmd = "echo '" + escapeForSingleQuote(json) + "' | xray api ado --server=127.0.0.1:"
                + node.getXrayGrpcPort() + " -";
        try {
            session.ssh().exec(cmd, OP_TIMEOUT);
        } catch (BusinessException be) {
            throw mapAddOutboundError(be, serverId, tag);
        }
    }

    /** 渲染 socks5 outbound JSON (含可选鉴权). */
    private String buildSocksOutboundJson(String tag, String host, int port,
                                          String username, String password) {
        JSONObject server = new JSONObject();
        server.put("address", host);
        server.put("port", port);
        if (StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password)) {
            JSONObject user = new JSONObject();
            user.put("user", username);
            user.put("pass", password);
            user.put("level", 0);
            JSONArray users = new JSONArray();
            users.add(user);
            server.put("users", users);
        }

        JSONArray servers = new JSONArray();
        servers.add(server);

        JSONObject settings = new JSONObject();
        settings.put("servers", servers);

        JSONObject outbound = new JSONObject();
        outbound.put("tag", tag);
        outbound.put("protocol", "socks");
        outbound.put("settings", settings);
        return outbound.toJSONString();
    }

    private BusinessException mapAddOutboundError(BusinessException be, String serverId, String tag) {
        String msg = StrUtil.blankToDefault(be.getMessage(), "");
        if (containsAny(msg, "already running", "already exists", "duplicate", "exist")) {
            return new BusinessException(XrayErrorCode.CLIENT_DUPLICATE, tag);
        }
        log.warn("[xray-cli] addOutbound 失败 server={} tag={} stderr={}", serverId, tag, msg);
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                serverId, "addOutbound: " + truncate(msg, 200));
    }

    private BusinessException mapRemoveOutboundError(BusinessException be, String serverId, String tag) {
        String msg = StrUtil.blankToDefault(be.getMessage(), "");
        if (containsAny(msg, "not found", "no such")) {
            return new BusinessException(XrayErrorCode.CLIENT_NOT_FOUND, tag);
        }
        log.warn("[xray-cli] removeOutbound 失败 server={} tag={} stderr={}", serverId, tag, msg);
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                serverId, "removeOutbound: " + truncate(msg, 200));
    }

    private static String escapeForSingleQuote(String s) {
        return s.replace("'", "'\\''");
    }

    private static String escapeShellArg(String s) {
        return "'" + escapeForSingleQuote(s) + "'";
    }

    private static boolean containsAny(String text, String... keys) {
        if (StrUtil.isBlank(text)) return false;
        String lower = text.toLowerCase();
        for (String k : keys) {
            if (lower.contains(k.toLowerCase())) return true;
        }
        return false;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
