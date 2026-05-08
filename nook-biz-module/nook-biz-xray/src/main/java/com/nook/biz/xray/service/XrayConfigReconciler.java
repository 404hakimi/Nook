package com.nook.biz.xray.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.nook.biz.resource.api.ResourceIpPoolApi;
import com.nook.biz.resource.api.dto.IpPoolEntryDTO;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.backend.XrayProtocol;
import com.nook.biz.xray.backend.dto.XrayClientSpec;
import com.nook.biz.xray.constant.XrayConstants;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.biz.xray.entity.XrayInbound;
import com.nook.biz.xray.mapper.XrayInboundMapper;
import com.nook.biz.xray.util.SshExecutor;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把 nook DB 的 client 状态投影到远端 xray.json 并触发 reload; 与 gRPC 运行时变更互补 —
 * gRPC 负责无重启加/删 user, 这里负责出站(socks5)与路由规则(按 email 分流)的持久化。
 *
 * <p>同步策略:
 * <ol>
 *   <li>SSH 拉远端当前 xray.json, 复用其 log/api/policy/stats/inbounds 配置 (尊重运营在远端的改动)</li>
 *   <li>对每个 inbound 重新填充 {@code settings.clients[]}, 按 protocol 派 {@link XrayProtocol#buildClientJson}</li>
 *   <li>outbounds 与 routing 完全由 nook 重生成: 每个 occupied IP 一个 socks5 outbound + 一条 email 分流 rule</li>
 *   <li>atomic 写回 + xray test 校验语法 + systemctl restart</li>
 * </ol>
 *
 * <p>Xray 不支持 SIGHUP, restart 会断现有连接 1-2s; 兑换/退订都是用户主动行为, 业务上接受。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XrayConfigReconciler {

    private final XrayInboundMapper xrayInboundMapper;
    private final ResourceIpPoolApi resourceIpPoolApi;
    private final SshExecutor sshExecutor;

    /**
     * 将指定 server 的 xray.json 与 DB 状态对齐并重启 xray; 失败抛 BusinessException, 调用方决定回滚。
     */
    public void reconcile(ServerCredentialDTO cred) {
        if (ObjectUtil.isNull(cred)) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, "<null>");
        }
        JSONObject remote = readRemoteConfig(cred);
        List<XrayInbound> rows = xrayInboundMapper.selectByServerId(cred.serverId());
        Map<String, List<XrayInbound>> byInboundTag = rows.stream()
                .filter(r -> StrUtil.isNotBlank(r.getExternalInboundRef()))
                .collect(Collectors.groupingBy(XrayInbound::getExternalInboundRef));

        repopulateInboundClients(remote, byInboundTag);
        remote.put("outbounds", buildOutbounds(rows));
        remote.put("routing", buildRouting(rows));

        String json = remote.toJSONString(JSONWriter.Feature.PrettyFormat,
                JSONWriter.Feature.WriteMapNullValue);
        pushAndReload(cred, json);
        log.info("[reconciler] OK server={} clients={} bytes={}",
                cred.serverId(), rows.size(), json.length());
    }

    // ===== 远端配置读取 =====

    private JSONObject readRemoteConfig(ServerCredentialDTO cred) {
        String json = sshExecutor.exec(cred,
                "cat " + XrayConstants.REMOTE_CONFIG_PATH,
                cred.sshTimeoutSeconds());
        if (StrUtil.isBlank(json)) {
            throw new BusinessException(XrayErrorCode.BACKEND_RESPONSE_INVALID,
                    cred.serverId(), "远端 xray.json 为空");
        }
        try {
            JSONObject root = JSONObject.parseObject(json);
            if (ObjectUtil.isNull(root)) {
                throw new BusinessException(XrayErrorCode.BACKEND_RESPONSE_INVALID,
                        cred.serverId(), "远端 xray.json 解析为空");
            }
            return root;
        } catch (JSONException e) {
            throw new BusinessException(XrayErrorCode.BACKEND_RESPONSE_INVALID, e,
                    cred.serverId(), "远端 xray.json 非法: " + e.getMessage());
        }
    }

    // ===== inbound clients 投影 =====

    /**
     * 用 DB 行替换每个 inbound 的 settings.clients[]; 不动 inbound 本身的 listen/port/streamSettings/protocol —
     * 这些属于服务器侧管理职责, 改它们要走部署脚本或运维台。
     */
    private void repopulateInboundClients(JSONObject root, Map<String, List<XrayInbound>> byInboundTag) {
        JSONArray inbounds = root.getJSONArray("inbounds");
        if (CollUtil.isEmpty(inbounds)) return;
        for (int i = 0; i < inbounds.size(); i++) {
            JSONObject inbound = inbounds.getJSONObject(i);
            if (ObjectUtil.isNull(inbound)) continue;
            String tag = inbound.getString("tag");
            if (StrUtil.isBlank(tag) || StrUtil.equals(tag, XrayConstants.API_TAG)) continue;
            String protocolCode = inbound.getString("protocol");
            if (StrUtil.isBlank(protocolCode)) continue;
            // 远端 inbound 找不到对应协议(冷僻协议) → 仍保留它的原 clients 不动, 不破坏运营手工管的 inbound
            XrayProtocol protocol;
            try {
                protocol = XrayProtocol.of(protocolCode);
            } catch (BusinessException be) {
                log.warn("[reconciler] inbound={} 协议 {} 暂不在多协议抽象中, 跳过 client 重建",
                        tag, protocolCode);
                continue;
            }
            JSONObject settings = inbound.getJSONObject("settings");
            if (ObjectUtil.isNull(settings)) {
                settings = new JSONObject();
                inbound.put("settings", settings);
            }
            settings.put("clients", buildClientArray(byInboundTag.get(tag), protocol));
        }
    }

    private JSONArray buildClientArray(List<XrayInbound> rows, XrayProtocol protocol) {
        JSONArray arr = new JSONArray();
        if (CollUtil.isEmpty(rows)) return arr;
        for (XrayInbound row : rows) {
            if (!StrUtil.equalsIgnoreCase(row.getProtocol(), protocol.getCode())) continue;
            arr.add(protocol.buildClientJson(toSpec(row)));
        }
        return arr;
    }

    private XrayClientSpec toSpec(XrayInbound row) {
        return XrayClientSpec.builder()
                .externalInboundRef(row.getExternalInboundRef())
                .email(row.getClientEmail())
                .uuid(row.getClientUuid())
                .protocol(row.getProtocol())
                .build();
    }

    // ===== outbounds + routing =====

    /** 每个 client 一条 socks5 outbound (拿不到 IP 凭据则跳过); 加 freedom api/direct 兜底。 */
    private JSONArray buildOutbounds(List<XrayInbound> rows) {
        JSONArray outbounds = new JSONArray();
        outbounds.add(freedomOutbound(XrayConstants.API_TAG));
        outbounds.add(freedomOutbound(XrayConstants.DIRECT_OUTBOUND_TAG));
        for (XrayInbound row : rows) {
            JSONObject socks = buildSocks5OutboundFor(row);
            if (ObjectUtil.isNotNull(socks)) outbounds.add(socks);
        }
        return outbounds;
    }

    private JSONObject buildSocks5OutboundFor(XrayInbound row) {
        IpPoolEntryDTO ip;
        try {
            ip = resourceIpPoolApi.loadEntry(row.getIpId());
        } catch (BusinessException be) {
            log.warn("[reconciler] 跳过 client={} ipId={} (IP 池条目不存在: {})",
                    row.getClientEmail(), row.getIpId(), be.getMessage());
            return null;
        }
        if (StrUtil.isBlank(ip.getSocks5Host()) || ObjectUtil.isNull(ip.getSocks5Port())) {
            log.warn("[reconciler] 跳过 client={} ipId={} (SOCKS5 凭据未配置)",
                    row.getClientEmail(), row.getIpId());
            return null;
        }
        JSONObject server = new JSONObject();
        server.put("address", ip.getSocks5Host());
        server.put("port", ip.getSocks5Port());
        if (StrUtil.isNotBlank(ip.getSocks5Username()) && StrUtil.isNotBlank(ip.getSocks5Password())) {
            JSONObject user = new JSONObject();
            user.put("user", ip.getSocks5Username());
            user.put("pass", ip.getSocks5Password());
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
        outbound.put("tag", userOutboundTag(row.getClientEmail()));
        outbound.put("protocol", "socks");
        outbound.put("settings", settings);
        return outbound;
    }

    private JSONObject freedomOutbound(String tag) {
        JSONObject o = new JSONObject();
        o.put("tag", tag);
        o.put("protocol", "freedom");
        return o;
    }

    /** api 通道直走 api outbound; 其余按 email 分流到该用户的 socks5 outbound, 不匹配的走 direct 兜底。 */
    private JSONObject buildRouting(List<XrayInbound> rows) {
        JSONObject routing = new JSONObject();
        routing.put("domainStrategy", "AsIs");
        JSONArray rules = new JSONArray();
        rules.add(apiRoutingRule());
        for (XrayInbound row : rows) {
            if (StrUtil.isBlank(row.getClientEmail())) continue;
            rules.add(emailRoutingRule(row));
        }
        routing.put("rules", rules);
        return routing;
    }

    private JSONObject apiRoutingRule() {
        JSONObject rule = new JSONObject();
        rule.put("type", "field");
        JSONArray inTags = new JSONArray();
        inTags.add(XrayConstants.API_TAG);
        rule.put("inboundTag", inTags);
        rule.put("outboundTag", XrayConstants.API_TAG);
        return rule;
    }

    private JSONObject emailRoutingRule(XrayInbound row) {
        JSONObject rule = new JSONObject();
        rule.put("type", "field");
        JSONArray inTags = new JSONArray();
        inTags.add(row.getExternalInboundRef());
        rule.put("inboundTag", inTags);
        JSONArray emails = new JSONArray();
        emails.add(row.getClientEmail());
        rule.put("user", emails);
        rule.put("outboundTag", userOutboundTag(row.getClientEmail()));
        return rule;
    }

    private String userOutboundTag(String email) {
        return XrayConstants.USER_OUTBOUND_TAG_PREFIX + email;
    }

    // ===== 上传 + reload =====

    private void pushAndReload(ServerCredentialDTO cred, String json) {
        String remoteTmp = XrayConstants.REMOTE_TMP_PREFIX + System.currentTimeMillis() + ".json";
        sshExecutor.uploadString(cred, remoteTmp, json, cred.sshTimeoutSeconds());
        // 一条 SSH 命令打通: chmod / 校验 / 原子 mv / restart / 等启动完成. 任一失败 abort, 残留 tmp 由 catch 清理。
        String cmd = String.join(" && ",
                "chmod 640 '" + remoteTmp + "'",
                "chown root:" + XrayConstants.SYSTEMD_UNIT + " '" + remoteTmp + "' 2>/dev/null || true",
                // 新版 Xray 用 'run -test', 老版兼容 'test'
                "(xray run -test -c '" + remoteTmp + "' >/dev/null 2>&1"
                        + " || xray test -c '" + remoteTmp + "' >/dev/null 2>&1)",
                "mv '" + remoteTmp + "' '" + XrayConstants.REMOTE_CONFIG_PATH + "'",
                "systemctl restart " + XrayConstants.SYSTEMD_UNIT,
                "sleep 1",
                "systemctl is-active " + XrayConstants.SYSTEMD_UNIT
        );
        try {
            sshExecutor.exec(cred, cmd, cred.sshTimeoutSeconds());
        } catch (BusinessException be) {
            log.error("[reconciler] 远端 reload 失败 server={}", cred.serverId(), be);
            cleanupQuietly(cred, remoteTmp);
            throw be;
        }
    }

    /** 残留临时文件清理; 静默吞错 — 我们不希望它把上层真正的失败原因覆盖掉。 */
    private void cleanupQuietly(ServerCredentialDTO cred, String remoteTmp) {
        try {
            sshExecutor.exec(cred, "rm -f '" + remoteTmp + "'", 10);
        } catch (RuntimeException cleanupErr) {
            log.warn("[reconciler] 清理临时文件失败 server={} path={}",
                    cred.serverId(), remoteTmp, cleanupErr);
        }
    }
}
