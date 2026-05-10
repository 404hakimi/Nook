package com.nook.biz.node.framework.xray.cli;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.xray.cli.utils.ShellEscapeUtils;
import com.nook.biz.node.framework.xray.inbound.config.InboundProtocolMapping;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xray inbound 整体增删 CLI 客户端 (走 SSH + xray api adi/rmi); 不查 DB, apiPort 由调用方传入.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayInboundCli {

    @Resource
    private SshSessionManager sshSessionManager;

    /**
     * 加一个完整 inbound (1:1 模型每客户独享一个 inbound, 仅 1 个 user); 已存在抛 CLIENT_DUPLICATE.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param tag      inbound tag (1:1 模型: in_slot_XX)
     * @param port     监听端口 (= node.slotPortBase + slot_index)
     * @param user     user 协议规格
     */
    public void addInbound(String serverId, int apiPort, String tag, int port, InboundUserSpec user) {
        String json = buildInboundJson(tag, port, user);
        SshSession session = sshSessionManager.acquire(serverId);
        // echo '<json>' | xray api adi --server=127.0.0.1:<apiPort> -; xray CLI 已封装 JSON → protobuf, 不走 gRPC vendor proto
        String cmd = "echo '" + ShellEscapeUtils.singleQuoteContent(json)
                + "' | xray api adi --server=127.0.0.1:" + apiPort + " -";
        try {
            session.ssh().exec(cmd);
            log.info("[xray-cli] addInbound server={} tag={} port={} protocol={} email={}",
                    serverId, tag, port, user.getProtocol(), user.getEmail());
        } catch (BusinessException be) {
            throw mapAddInboundError(be, serverId, tag);
        }
    }

    /**
     * 删一个 inbound (按 tag); tag 不存在抛 CLIENT_NOT_FOUND.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param tag      inbound tag
     */
    public void removeInbound(String serverId, int apiPort, String tag) {
        SshSession session = sshSessionManager.acquire(serverId);
        String cmd = "xray api rmi --server=127.0.0.1:" + apiPort + " "
                + ShellEscapeUtils.shellArg(tag);
        try {
            session.ssh().exec(cmd);
            log.info("[xray-cli] removeInbound server={} tag={}", serverId, tag);
        } catch (BusinessException be) {
            throw mapRemoveInboundError(be, serverId, tag);
        }
    }

    /**
     * 渲染 inbound JSON; 阶段 1 简化为 vless+TCP+无 REALITY, 后续加 streamSettings 入口再扩展.
     *
     * @param tag  inbound tag
     * @param port 监听端口
     * @param user user 协议规格
     * @return inbound 完整 JSON 字符串
     */
    private String buildInboundJson(String tag, int port, InboundUserSpec user) {
        InboundProtocolMapping protocol = InboundProtocolMapping.of(user.getProtocol());
        JSONObject client = protocol.buildClientJson(user);

        JSONObject settings = new JSONObject();
        JSONArray clients = new JSONArray();
        clients.add(client);
        settings.put("clients", clients);
        // vless 的 settings 必须有 decryption 字段, 当前固定 "none"
        if ("vless".equalsIgnoreCase(user.getProtocol())) {
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
        inbound.put("protocol", user.getProtocol().toLowerCase());
        inbound.put("settings", settings);
        inbound.put("streamSettings", stream);
        inbound.put("sniffing", sniffing);

        return inbound.toJSONString();
    }

    /**
     * 把 CLI add 的 BusinessException 翻译成业务错误码 (识别 "already exists" 等关键词归入 CLIENT_DUPLICATE).
     *
     * @param be       原始异常
     * @param serverId resource_server.id
     * @param tag      inbound tag
     * @return 翻译后的 BusinessException
     */
    private BusinessException mapAddInboundError(BusinessException be, String serverId, String tag) {
        String msg = StrUtil.blankToDefault(be.getMessage(), "");
        if (StrUtil.containsAnyIgnoreCase(msg, "already running", "already exists", "duplicate", "exist")) {
            return new BusinessException(XrayErrorCode.CLIENT_DUPLICATE, tag);
        }
        log.warn("[xray-cli] addInbound 失败 server={} tag={} stderr={}", serverId, tag, msg);
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                serverId, "addInbound: " + StrUtil.maxLength(msg, 200));
    }

    /**
     * 把 CLI remove 的 BusinessException 翻译成业务错误码 (识别 "not found" 归入 CLIENT_NOT_FOUND).
     *
     * @param be       原始异常
     * @param serverId resource_server.id
     * @param tag      inbound tag
     * @return 翻译后的 BusinessException
     */
    private BusinessException mapRemoveInboundError(BusinessException be, String serverId, String tag) {
        String msg = StrUtil.blankToDefault(be.getMessage(), "");
        if (StrUtil.containsAnyIgnoreCase(msg, "not found", "no such")) {
            return new BusinessException(XrayErrorCode.CLIENT_NOT_FOUND, tag);
        }
        log.warn("[xray-cli] removeInbound 失败 server={} tag={} stderr={}", serverId, tag, msg);
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                serverId, "removeInbound: " + StrUtil.maxLength(msg, 200));
    }
}
