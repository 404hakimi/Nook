package com.nook.biz.node.framework.xray.cli;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.framework.ssh.core.SshSession;
import com.nook.biz.node.framework.xray.cli.utils.ShellEscapeUtils;
import com.nook.biz.node.framework.xray.inbound.config.InboundProtocolMapping;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xray inbound 整体增删 CLI 客户端 (走 SSH + xray api adi/rmi); 由 caller 传入已 acquire 的 session.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayInboundCli {

    /**
     * 加一个完整 inbound; 已存在抛 CLIENT_DUPLICATE.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param apiPort xray 内置 api server 端口
     * @param tag     inbound tag
     * @param port    监听端口
     * @param user    user 协议规格
     */
    public void addInbound(SshSession session, String xrayBin, int apiPort, String tag, int port, InboundUserSpec user) {
        String json = buildInboundJson(tag, port, user);
        String cmd = buildAdiCmd(xrayBin, apiPort, json);
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout();
        } catch (BusinessException be) {
            throw mapAddInboundError(be, session.serverId(), tag);
        }
        // adi 每条 inbound 处理时 stdout 会 echo "adding: <tag>"; 没看到说明 xray 收到空 inbounds 数组,
        // 这种异常没法靠 exit code 兜底, 主动校验防 adu 同款静默失败.
        if (!StrUtil.contains(stdout, "adding: " + tag)) {
            log.warn("[xray-cli] addInbound 静默失败 server={} tag={} stdout={}",
                    session.serverId(), tag, StrUtil.maxLength(stdout, 400));
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    session.serverId(), "addInbound 远端未确认 (stdout: "
                            + StrUtil.maxLength(stdout, 200) + ")");
        }
        log.info("[xray-cli] addInbound server={} tag={} port={} protocol={} email={}",
                session.serverId(), tag, port, user.getProtocol(), user.getEmail());
    }

    /**
     * 列远端所有 inbound tag (走 xray api lsi); 用于 reconciler 跟 DB 对账.
     *
     * <p><b>失败必抛</b>: SSH 抖动 / xray 没起 / jq 缺失 → 上层 (replayInternal) 必须放弃本轮,
     * 不能拿空集继续 — 空集会被误判成"远端啥都没有", 触发 needSync=全部, 全 server 客户断连重建.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param apiPort xray 内置 api server 端口
     * @return tag 列表 (含静态预置如 api, 调用方按需过滤)
     * @throws BusinessException SSH/xray 不可用; 调用方应放弃本轮 reconcile
     */
    public List<String> listInbounds(SshSession session, String xrayBin, int apiPort) {
        // 不再用 "|| true" 兜底; xray lsi 失败 (非 0 exit) 必须传上去
        String cmd = xrayBin + " api lsi --server=127.0.0.1:" + apiPort + " | jq -r '.inbounds[].tag'";
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout();
        } catch (RuntimeException e) {
            log.warn("[xray-cli] listInbounds 失败 server={}: {}", session.serverId(), e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, e,
                    session.serverId(), "listInbounds: " + StrUtil.maxLength(e.getMessage(), 200));
        }
        if (StrUtil.isBlank(stdout)) return Collections.emptyList();
        return Arrays.stream(stdout.split("\\R"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    /**
     * 列指定 inbound 上现有 user 的 email 集合 (xray api inbounduser -tag=xxx).
     *
     * <p>用于 sync-status 对账 1:N 共享 inbound 上 user 是否跟 DB 客户名单对齐;
     * inbound 上无 user 时远端返回 {@code {}}, 此处返空集.
     *
     * @param session    caller 已 acquire 的 SSH 会话
     * @param apiPort    xray 内置 api server 端口
     * @param inboundTag 目标 inbound tag
     * @return user email 集合 (可能为空)
     * @throws BusinessException SSH / xray 不可用; 调用方应放弃本轮对账
     */
    public Set<String> listUsers(SshSession session, String xrayBin, int apiPort, String inboundTag) {
        String cmd = xrayBin + " api inbounduser --server=127.0.0.1:" + apiPort
                + " -tag=" + ShellEscapeUtils.shellArg(inboundTag)
                + " | jq -r '.users[]?.email // empty'";
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout();
        } catch (RuntimeException e) {
            log.warn("[xray-cli] listUsers 失败 server={} inbound={}: {}",
                    session.serverId(), inboundTag, e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, e,
                    session.serverId(), "listUsers: " + StrUtil.maxLength(e.getMessage(), 200));
        }
        if (StrUtil.isBlank(stdout)) return Collections.emptySet();
        return Arrays.stream(stdout.split("\\R"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 加 user 到现有 inbound (xray api adu); inbound 必须已存在.
     *
     * <p><b>静默失败防御</b>: xray adu 即使 user 没真正加进去 (config validator 拒了 inbound 描述等),
     * 命令本身仍 exit 0, 仅在 stdout 输出 "Added 0 user(s) in total.". 这里抓 stdout 主动校验,
     * Added != 1 直接抛错 — 避免上层以为 provision 成功但远端 inbound 仍 0 user.
     *
     * @param session    caller 已 acquire 的 SSH 会话
     * @param apiPort    xray 内置 api server 端口
     * @param inboundTag 目标 inbound tag
     * @param user       user 协议规格 (id, email 用于路由 / 流量统计)
     */
    public void addUser(SshSession session, String xrayBin, int apiPort, String inboundTag, InboundUserSpec user) {
        String json = buildUserOnlyInboundJson(inboundTag, user);
        String cmd = buildAduCmd(xrayBin, apiPort, json);
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout();
        } catch (BusinessException be) {
            throw mapAddInboundError(be, session.serverId(), inboundTag);
        }
        if (!StrUtil.contains(stdout, "Added 1 user(s)")) {
            log.warn("[xray-cli] addUser 静默失败 server={} inbound={} email={} stdout={}",
                    session.serverId(), inboundTag, user.getEmail(),
                    StrUtil.maxLength(stdout, 400));
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    session.serverId(), "addUser 实际加入 0 个 user; 看远端 xray 日志 (stdout: "
                            + StrUtil.maxLength(stdout, 200) + ")");
        }
        log.info("[xray-cli] addUser server={} inbound={} email={} id={}",
                session.serverId(), inboundTag, user.getEmail(), user.getUuid());
    }

    /**
     * 从 inbound 删 user (xray api rmu -tag=xxx &lt;email&gt;).
     *
     * @param session    caller 已 acquire 的 SSH 会话
     * @param apiPort    xray 内置 api server 端口
     * @param inboundTag 目标 inbound tag
     * @param email      要删的 user email
     */
    public void removeUser(SshSession session, String xrayBin, int apiPort, String inboundTag, String email) {
        String cmd = xrayBin + " api rmu --server=127.0.0.1:" + apiPort
                + " -tag=" + ShellEscapeUtils.shellArg(inboundTag) + " "
                + ShellEscapeUtils.shellArg(email);
        try {
            session.ssh().exec(cmd);
            log.info("[xray-cli] removeUser server={} inbound={} email={}",
                    session.serverId(), inboundTag, email);
        } catch (BusinessException be) {
            throw mapRemoveInboundError(be, session.serverId(), email);
        }
    }

    /**
     * 删一个 inbound (按 tag); tag 不存在抛 CLIENT_NOT_FOUND.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param apiPort xray 内置 api server 端口
     * @param tag     inbound tag
     */
    public void removeInbound(SshSession session, String xrayBin, int apiPort, String tag) {
        String cmd = xrayBin + " api rmi --server=127.0.0.1:" + apiPort + " "
                + ShellEscapeUtils.shellArg(tag);
        try {
            session.ssh().exec(cmd);
            log.info("[xray-cli] removeInbound server={} tag={}", session.serverId(), tag);
        } catch (BusinessException be) {
            throw mapRemoveInboundError(be, session.serverId(), tag);
        }
    }

    /**
     * 渲染 xray adi 入参 JSON; 顶层必须是 {"inbounds": [...]} 容器, xray 把它当 Config 对象整段解析.
     * 阶段 1 简化为 vless+TCP+无 REALITY, 后续加 streamSettings 入口再扩展.
     *
     * @param tag  inbound tag
     * @param port 监听端口
     * @param user user 协议规格
     * @return 含顶层 "inbounds" 数组的完整 JSON 字符串
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

        // xray adi 期望顶层是 Config 对象, "inbounds" 数组为空时报 "no valid inbound found"
        JSONArray inbounds = new JSONArray();
        inbounds.add(inbound);
        JSONObject config = new JSONObject();
        config.put("inbounds", inbounds);
        return config.toJSONString();
    }

    /**
     * 渲染 adi 命令; 用 xray-core 文档化的 {@code stdin:} 语法显式声明输入源.
     *
     * <p>v26.3.27 源码里 adi/ado/adrules 在 unnamedArgs 为空时会自动补 {@code "stdin:"}, 但这是隐式
     * fallback (未在 README 说明), 跨版本不可靠. 跟 adu (无此 fallback) 走同样的显式语法, 避免再踩雷.
     */
    private String buildAdiCmd(String xrayBin, int apiPort, String json) {
        return buildApiFromStdinCmd(xrayBin, apiPort, "adi", json, null);
    }

    /**
     * 通用: 把 JSON 通过 base64 喂给 xray api 子命令, 显式传 {@code stdin:} 让其从 stdin 读.
     *
     * @param extraArgs subCmd 后追加的参数 (如 {@code --append}), null/空则跳过; {@code stdin:} 位置在最后
     */
    private String buildApiFromStdinCmd(String xrayBin, int apiPort, String subCmd, String json, String extraArgs) {
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        sb.append("echo '").append(b64).append("' | base64 -d | ")
                .append(xrayBin).append(" api ").append(subCmd)
                .append(" --server=127.0.0.1:").append(apiPort);
        if (StrUtil.isNotBlank(extraArgs)) {
            sb.append(' ').append(extraArgs);
        }
        sb.append(" stdin:");
        return sb.toString();
    }

    /** adu 专用 (跟 adi 共用 stdin: 语法, 但不带 extraArgs). */
    private String buildAduCmd(String xrayBin, int apiPort, String json) {
        return buildApiFromStdinCmd(xrayBin, apiPort, "adu", json, null);
    }

    /**
     * 渲染 adu 入参 JSON: {"inbounds": [{tag, listen, port, protocol, settings:{clients:[...]}}]}.
     *
     * <p><b>关键陷阱</b>: xray-core 的 {@code adu} 子命令把整段 JSON 当一份完整 inbound config 走 validator,
     * 而非只取 settings.clients. 缺 listen / port 字段会被 validator 静默拒 (报 "Listen on AnyIP but no Port(s) set"
     * 但命令仍 exit 0 + 输出 "Added 0 user(s)"), 上层 SSH 看不到错误, user 一直没真正注册.
     * 这里 listen=0.0.0.0 + port=1 是占位 (xray 已按 tag 匹配运行中的 inbound, 真实监听端口不变); 让 validator 放行而已.
     *
     * <p>字段名 {@code clients} 是 vmess/vless/trojan inbound 在 xray.json 里的标准 user 列表字段, 不能用 {@code users}.
     */
    public String buildUserOnlyInboundJson(String tag, InboundUserSpec user) {
        InboundProtocolMapping protocol = InboundProtocolMapping.of(user.getProtocol());
        JSONObject userJson = protocol.buildClientJson(user);

        JSONArray clients = new JSONArray();
        clients.add(userJson);
        JSONObject settings = new JSONObject();
        settings.put("clients", clients);
        if ("vless".equalsIgnoreCase(user.getProtocol())) {
            settings.put("decryption", "none");
        }

        JSONObject inbound = new JSONObject();
        inbound.put("tag", tag);
        inbound.put("listen", "0.0.0.0");
        inbound.put("port", 1);
        inbound.put("protocol", user.getProtocol().toLowerCase());
        inbound.put("settings", settings);

        JSONArray inbounds = new JSONArray();
        inbounds.add(inbound);
        JSONObject config = new JSONObject();
        config.put("inbounds", inbounds);
        return config.toJSONString();
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
     * 把 CLI remove 的 BusinessException 翻译成业务错误码; "目标不在" 一类统一归入 CLIENT_NOT_FOUND.
     *
     * <p>xray 找不到 inbound 时除 "not found" 还会回:
     * "common: not enough information for making a decision" (dispatcher 解析失败的兜底文案);
     * 视为 inbound 已不在, 让 sync/revoke/rotate 的幂等路径继续, 避免误报失败.
     *
     * @param be       原始异常
     * @param serverId resource_server.id
     * @param tag      inbound tag
     * @return 翻译后的 BusinessException
     */
    private BusinessException mapRemoveInboundError(BusinessException be, String serverId, String tag) {
        String msg = StrUtil.blankToDefault(be.getMessage(), "");
        if (StrUtil.containsAnyIgnoreCase(msg,
                "not found", "no such", "not enough information", "no inbound", "inbound not exist")) {
            return new BusinessException(XrayErrorCode.CLIENT_NOT_FOUND, tag);
        }
        log.warn("[xray-cli] removeInbound 失败 server={} tag={} stderr={}", serverId, tag, msg);
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                serverId, "removeInbound: " + StrUtil.maxLength(msg, 200));
    }
}
