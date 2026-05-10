package com.nook.biz.node.framework.xray.handler;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.xray.inbound.config.InboundProtocolMapping;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Xray inbound 整体增删 CLI 客户端 (走 SSH + `xray api adi/rmi`).
 *
 * <p>之所以走 CLI 而非纯 gRPC: AddInbound 的 protobuf 嵌套 (InboundHandlerConfig + transport/internet 等)
 * 体量太大, vendor 全套 proto + 写嵌套 builder 不划算; xray CLI 已经把"JSON → InboundHandlerConfig protobuf"
 * 这套封装好了, nook 直接喂 JSON 即可. 性能 ~150ms/call, 业务低频可接受.
 *
 * <p>本类只管"加/删整个 inbound"; user 级别的细粒度 (rotate uuid 等) 走 {@link com.nook.biz.node.framework.xray.grpc.XrayInboundClient}
 * 的 gRPC AlterInbound, 性能更优.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayInboundCliClient {

    /** 单条 SSH+CLI 命令默认超时. */
    private static final Duration OP_TIMEOUT = Duration.ofSeconds(30);

    @Resource
    private SshSessionManager sshSessionManager;
    @Resource
    private XrayNodeService xrayNodeService;

    /**
     * 在指定 server 上加一个完整 inbound (1:1 模型每客户一个独立 inbound, 仅 1 个 user).
     *
     * <p>tag 已存在抛 CLIENT_DUPLICATE; 远端 listen 端口被占抛 BACKEND_OPERATION_FAILED.
     *
     * @param serverId resource_server.id
     * @param tag      inbound tag (1:1 模型: in_slot_XX)
     * @param port     监听端口 (= node.slotPortBase + slot_index)
     * @param user     user 协议规格 (含 uuid/email/protocol)
     */
    public void addInbound(String serverId, String tag, int port, InboundUserSpec user) {
        XrayNodeDO node = xrayNodeService.loadOrThrow(serverId);
        String json = buildInboundJson(tag, port, user);
        SshSession session = sshSessionManager.acquire(serverId);
        // echo '<json>' | xray api adi --server=127.0.0.1:<grpcPort> -
        String cmd = "echo '" + escapeForSingleQuote(json) + "' | xray api adi --server=127.0.0.1:"
                + node.getXrayGrpcPort() + " -";
        try {
            session.ssh().exec(cmd, OP_TIMEOUT);
            log.info("[xray-cli] addInbound server={} tag={} port={} protocol={} email={}",
                    serverId, tag, port, user.protocol(), user.email());
        } catch (BusinessException be) {
            throw mapAddInboundError(be, serverId, tag);
        }
    }

    /**
     * 在指定 server 上删一个 inbound (按 tag).
     *
     * <p>tag 不存在抛 CLIENT_NOT_FOUND.
     *
     * @param serverId resource_server.id
     * @param tag      inbound tag
     */
    public void removeInbound(String serverId, String tag) {
        XrayNodeDO node = xrayNodeService.loadOrThrow(serverId);
        SshSession session = sshSessionManager.acquire(serverId);
        String cmd = "xray api rmi --server=127.0.0.1:" + node.getXrayGrpcPort() + " "
                + escapeShellArg(tag);
        try {
            session.ssh().exec(cmd, OP_TIMEOUT);
            log.info("[xray-cli] removeInbound server={} tag={}", serverId, tag);
        } catch (BusinessException be) {
            throw mapRemoveInboundError(be, serverId, tag);
        }
    }

    // ===== 私有 =====

    /** 渲染 inbound JSON; 阶段 1 简化为 vless+TCP+无 REALITY, 后续加 streamSettings 入口再扩展. */
    private String buildInboundJson(String tag, int port, InboundUserSpec user) {
        InboundProtocolMapping protocol = InboundProtocolMapping.of(user.protocol());
        JSONObject client = protocol.buildClientJson(user);

        JSONObject settings = new JSONObject();
        JSONArray clients = new JSONArray();
        clients.add(client);
        settings.put("clients", clients);
        // vless 的 settings 必须有 decryption 字段, 当前固定 "none"
        if ("vless".equalsIgnoreCase(user.protocol())) {
            settings.put("decryption", "none");
        }

        JSONObject stream = new JSONObject();
        stream.put("network", "tcp");
        stream.put("security", "none");

        JSONObject sniffing = new JSONObject();
        sniffing.put("enabled", true);
        JSONArray destOverride = new JSONArray();
        destOverride.add("http");
        destOverride.add("tls");
        sniffing.put("destOverride", destOverride);

        JSONObject inbound = new JSONObject();
        inbound.put("tag", tag);
        inbound.put("listen", "0.0.0.0");
        inbound.put("port", port);
        inbound.put("protocol", user.protocol().toLowerCase());
        inbound.put("settings", settings);
        inbound.put("streamSettings", stream);
        inbound.put("sniffing", sniffing);

        return inbound.toJSONString();
    }

    /** addInbound 的 stderr 通常含 "already running" / "exists" / "duplicate", 命中即视为已存在. */
    private BusinessException mapAddInboundError(BusinessException be, String serverId, String tag) {
        String msg = StrUtil.blankToDefault(be.getMessage(), "");
        if (containsAny(msg, "already running", "already exists", "duplicate", "exist")) {
            return new BusinessException(XrayErrorCode.CLIENT_DUPLICATE, tag);
        }
        log.warn("[xray-cli] addInbound 失败 server={} tag={} stderr={}", serverId, tag, msg);
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                serverId, "addInbound: " + truncate(msg, 200));
    }

    /** removeInbound 的 stderr 通常含 "not found", 命中即视为已不存在. */
    private BusinessException mapRemoveInboundError(BusinessException be, String serverId, String tag) {
        String msg = StrUtil.blankToDefault(be.getMessage(), "");
        if (containsAny(msg, "not found", "no such")) {
            return new BusinessException(XrayErrorCode.CLIENT_NOT_FOUND, tag);
        }
        log.warn("[xray-cli] removeInbound 失败 server={} tag={} stderr={}", serverId, tag, msg);
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                serverId, "removeInbound: " + truncate(msg, 200));
    }

    /** 包成单引号字符串里安全的内容: 单引号本身用 '\'' 转义. */
    private static String escapeForSingleQuote(String s) {
        return s.replace("'", "'\\''");
    }

    /** 整个 token 作为 shell 单引号参数. */
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
