package com.nook.biz.node.framework.xray.cli;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.xray.cli.utils.ShellEscapeUtils;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Xray outbound 增删 CLI 客户端 (走 SSH + xray api ado/rmo); 不查 DB, apiPort 由调用方传入.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayOutboundCli {

    @Resource
    private SshSessionManager sshSessionManager;

    /**
     * 加 socks5 出站 (provision 时把客户流量打到独享落地 IP).
     *
     * @param serverId  resource_server.id
     * @param apiPort   xray 内置 api server 端口
     * @param tag       outbound tag
     * @param socksHost 落地 socks5 主机
     * @param socksPort 落地 socks5 端口
     * @param username  socks5 鉴权用户 (可空)
     * @param password  socks5 鉴权密码 (可空)
     */
    public void addSocksOutbound(String serverId, int apiPort, String tag,
                                 String socksHost, int socksPort,
                                 String username, String password) {
        String json = buildSocksOutboundJson(tag, socksHost, socksPort, username, password);
        execAdo(serverId, apiPort, tag, json);
        log.info("[xray-cli] addSocksOutbound server={} tag={} socks={}:{}",
                serverId, tag, socksHost, socksPort);
    }

    /**
     * 加 freedom 直连出站 (api 通道 / placeholder 占位 / revoke 后还原占位).
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param tag      outbound tag
     */
    public void addFreedomOutbound(String serverId, int apiPort, String tag) {
        JSONObject outbound = new JSONObject();
        outbound.put("tag", tag);
        outbound.put("protocol", "freedom");
        execAdo(serverId, apiPort, tag, wrapAsConfig(outbound));
        log.info("[xray-cli] addFreedomOutbound server={} tag={}", serverId, tag);
    }

    /**
     * 删 outbound (按 tag); tag 不存在抛 CLIENT_NOT_FOUND.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param tag      outbound tag
     */
    public void removeOutbound(String serverId, int apiPort, String tag) {
        SshSession session = sshSessionManager.acquire(serverId);
        String cmd = "xray api rmo --server=127.0.0.1:" + apiPort + " "
                + ShellEscapeUtils.shellArg(tag);
        try {
            session.ssh().exec(cmd);
            log.info("[xray-cli] removeOutbound server={} tag={}", serverId, tag);
        } catch (BusinessException be) {
            throw mapRemoveOutboundError(be, serverId, tag);
        }
    }

    /**
     * 共用 ado 命令封装; 把 config JSON 经 base64 喂给 xray api ado 的 stdin (新版 xray 不传文件参数即读 stdin).
     * 没有临时文件 / race / 残留风险, pipe 的 exit code 取末段 (xray) 的, 默认行为.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param tag      outbound tag (仅用于日志)
     * @param json     已包装的 config JSON 字符串, 顶层须为 {"outbounds":[...]} 格式
     */
    private void execAdo(String serverId, int apiPort, String tag, String json) {
        SshSession session = sshSessionManager.acquire(serverId);
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String cmd = "echo '" + b64 + "' | base64 -d | xray api ado --server=127.0.0.1:" + apiPort;
        try {
            session.ssh().exec(cmd);
        } catch (BusinessException be) {
            throw mapAddOutboundError(be, serverId, tag);
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
    private String buildSocksOutboundJson(String tag, String host, int port,
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
