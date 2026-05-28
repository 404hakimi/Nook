package com.nook.biz.node.framework.xray.cli;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.framework.ssh.core.SshSession;
import com.nook.biz.node.framework.xray.cli.utils.ShellEscapeUtils;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Xray outbound 增删 CLI 客户端 (走 SSH + xray api ado/rmo); 由 caller 传入已 acquire 的 session.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayOutboundCli {

    /**
     * 加 socks5 出站 (provision 时把客户流量打到独享落地 IP).
     *
     * @param session   caller 已 acquire 的 SSH 会话
     * @param apiPort   xray 内置 api server 端口
     * @param tag       outbound tag
     * @param socksHost 落地 socks5 主机
     * @param socksPort 落地 socks5 端口
     * @param username  socks5 鉴权用户 (可空)
     * @param password  socks5 鉴权密码 (可空)
     */
    public void addSocksOutbound(SshSession session, String xrayBin, int apiPort, String tag,
                                 String socksHost, int socksPort,
                                 String username, String password) {
        String json = buildSocksOutboundJson(tag, socksHost, socksPort, username, password);
        String stdout = execAdo(session, xrayBin, apiPort, tag, json);
        // ado 每条 outbound 处理时 stdout 会 echo "adding: <tag>"; 缺则视为 xray 解析到空 outbounds 数组,
        // 此情况 xray 仍 exit 0, 无法靠 exit code 兜底 (跟 adu 同款静默坑).
        if (!StrUtil.contains(stdout, "adding: " + tag)) {
            log.warn("[xray-cli] addSocksOutbound 静默失败 server={} tag={} stdout={}",
                    session.serverId(), tag, StrUtil.maxLength(stdout, 400));
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    session.serverId(), "addOutbound 远端未确认 (stdout: "
                            + StrUtil.maxLength(stdout, 200) + ")");
        }
        log.info("[xray-cli] addSocksOutbound server={} tag={} socks={}:{}",
                session.serverId(), tag, socksHost, socksPort);
    }

    /**
     * 列远端所有 outbound, 返回 {@code tag → 协议类别 (socks / other)}.
     *
     * <p>{@code xray.proxy.socks.Config} → {@code "socks"} (per-user 业务出站); 其它 (blackhole / freedom / api) 一律归为 {@code "other"}.
     * sync-status 对账只关心 socks 类型的业务出站 (tag = clientId), 静态 outbound (blackhole/api) 不参与对账.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param apiPort xray 内置 api server 端口
     * @return tag → kind 映射 (顺序保留, 便于 UI 展示)
     * @throws BusinessException SSH / xray 不可用; 调用方应放弃本轮对账
     */
    public Map<String, String> listOutbounds(SshSession session, String xrayBin, int apiPort) {
        // jq 把 (tag, _TypedMessage_) 平铺成一行用 tab 分隔, 远端 stdout 行数即出站条目数
        String cmd = xrayBin + " api lso --server=127.0.0.1:" + apiPort
                + " | jq -r '.outbounds[] | \"\\(.tag)\\t\\(.proxySettings._TypedMessage_ // \"\")\"'";
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout();
        } catch (RuntimeException e) {
            log.warn("[xray-cli] listOutbounds 失败 server={}: {}",
                    session.serverId(), e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, e,
                    session.serverId(), "listOutbounds: " + StrUtil.maxLength(e.getMessage(), 200));
        }
        if (StrUtil.isBlank(stdout)) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : stdout.split("\\R")) {
            if (StrUtil.isBlank(line)) continue;
            String[] parts = line.split("\\t", 2);
            String tag = parts[0].trim();
            String typedMsg = parts.length > 1 ? parts[1].trim() : "";
            out.put(tag, classifyOutbound(typedMsg));
        }
        return out;
    }

    /** 把 lso 的 _TypedMessage_ 字符串映射到业务侧 kind (只区分 socks vs other). */
    private static String classifyOutbound(String typedMessage) {
        return StrUtil.containsIgnoreCase(typedMessage, "socks") ? "socks" : "other";
    }

    /**
     * 删 outbound (按 tag); tag 不存在抛 CLIENT_NOT_FOUND.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param apiPort xray 内置 api server 端口
     * @param tag     outbound tag
     */
    public void removeOutbound(SshSession session, String xrayBin, int apiPort, String tag) {
        String cmd = xrayBin + " api rmo --server=127.0.0.1:" + apiPort + " "
                + ShellEscapeUtils.shellArg(tag);
        try {
            session.ssh().exec(cmd);
            log.info("[xray-cli] removeOutbound server={} tag={}", session.serverId(), tag);
        } catch (BusinessException be) {
            throw mapRemoveOutboundError(be, session.serverId(), tag);
        }
    }

    /**
     * 共用 ado 命令封装; 经 base64 把 config JSON 喂给 xray, 用 xray-core 文档化的 {@code stdin:} 显式语法.
     *
     * <p>v26.3.27 源码 ado 在 unnamedArgs 为空时会自动补 {@code "stdin:"}, 但这是隐式 fallback,
     * 显式传 {@code stdin:} 跟 adu 风格一致, 跨版本稳健.
     *
     * @return ado stdout (调用方靠 "adding: <tag>" 字样做 sanity-check)
     */
    private String execAdo(SshSession session, String xrayBin, int apiPort, String tag, String json) {
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String cmd = "echo '" + b64 + "' | base64 -d | " + xrayBin
                + " api ado --server=127.0.0.1:" + apiPort + " stdin:";
        try {
            return session.ssh().exec(cmd).getStdout();
        } catch (BusinessException be) {
            throw mapAddOutboundError(be, session.serverId(), tag);
        }
    }

    /**
     * 把 outbound 对象包成 xray Config 顶层结构 {"outbounds":[...]}, ado 解析时只识别这种格式.
     *
     * @param outbound outbound 对象
     * @return 含顶层 "outbounds" 数组的完整 JSON 字符串
     */
    private static String wrapAsConfig(JSONObject outbound) {
        JSONArray outbounds = new JSONArray();
        outbounds.add(outbound);
        JSONObject config = new JSONObject();
        config.put("outbounds", outbounds);
        return config.toJSONString();
    }

    /**
     * 渲染 socks5 outbound JSON (含可选鉴权).
     *
     * @param tag      outbound tag
     * @param host     落地 socks5 主机
     * @param port     落地 socks5 端口
     * @param username socks5 鉴权用户 (可空 = 无鉴权)
     * @param password socks5 鉴权密码 (可空 = 无鉴权)
     * @return 含顶层 "outbounds" 数组的完整 JSON 字符串
     */
    public String buildSocksOutboundJson(String tag, String host, int port,
                                          String username, String password) {
        // v25.10.15 起 outbound 强制 "1 endpoint + 至多 1 user", 旧的 servers[]/users[] 数组写法被禁,
        // 新版要求扁平: settings 直接平铺 address / port / user / pass.
        JSONObject settings = new JSONObject();
        settings.put("address", host);
        settings.put("port", port);
        if (StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password)) {
            settings.put("user", username);
            settings.put("pass", password);
            settings.put("level", 0);
        }

        JSONObject outbound = new JSONObject();
        outbound.put("tag", tag);
        outbound.put("protocol", "socks");
        outbound.put("settings", settings);
        return wrapAsConfig(outbound);
    }

    /**
     * 把 CLI add 的 BusinessException 翻译成业务错误码 (识别 "already exists" 归入 CLIENT_DUPLICATE).
     *
     * @param be       原始异常
     * @param serverId resource_server.id
     * @param tag      outbound tag
     * @return 翻译后的 BusinessException
     */
    private BusinessException mapAddOutboundError(BusinessException be, String serverId, String tag) {
        String msg = StrUtil.blankToDefault(be.getMessage(), "");
        if (StrUtil.containsAnyIgnoreCase(msg, "already running", "already exists", "duplicate", "exist")) {
            return new BusinessException(XrayErrorCode.CLIENT_DUPLICATE, tag);
        }
        log.warn("[xray-cli] addOutbound 失败 server={} tag={} stderr={}", serverId, tag, msg);
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                serverId, "addOutbound: " + StrUtil.maxLength(msg, 200));
    }

    /**
     * 把 CLI remove 的 BusinessException 翻译成业务错误码 (识别 "not found" 归入 CLIENT_NOT_FOUND).
     *
     * @param be       原始异常
     * @param serverId resource_server.id
     * @param tag      outbound tag
     * @return 翻译后的 BusinessException
     */
    private BusinessException mapRemoveOutboundError(BusinessException be, String serverId, String tag) {
        String msg = StrUtil.blankToDefault(be.getMessage(), "");
        if (StrUtil.containsAnyIgnoreCase(msg, "not found", "no such")) {
            return new BusinessException(XrayErrorCode.CLIENT_NOT_FOUND, tag);
        }
        log.warn("[xray-cli] removeOutbound 失败 server={} tag={} stderr={}", serverId, tag, msg);
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                serverId, "removeOutbound: " + StrUtil.maxLength(msg, 200));
    }
}
