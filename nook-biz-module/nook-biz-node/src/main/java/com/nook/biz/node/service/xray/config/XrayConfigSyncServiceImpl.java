package com.nook.biz.node.service.xray.config;

import jakarta.annotation.Resource;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.mysql.client.XrayClientMapper;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.xray.RemoteFiles;
import com.nook.biz.node.framework.xray.inbound.config.InboundConfigReconciler;
import com.nook.biz.node.framework.xray.outbound.OutboundConfigReconciler;
import com.nook.biz.node.framework.xray.routing.RoutingConfigReconciler;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class XrayConfigSyncServiceImpl implements XrayConfigSyncService {

    /** 单条远端命令默认超时. */
    private static final Duration OP_TIMEOUT = Duration.ofSeconds(60);

    @Resource
    private XrayClientMapper xrayClientMapper;
    @Resource
    private SshSessionManager sessionManager;
    @Resource
    private InboundConfigReconciler inboundConfigReconciler;
    @Resource
    private OutboundConfigReconciler outboundConfigReconciler;
    @Resource
    private RoutingConfigReconciler routingConfigReconciler;

    @Override
    public void sync(String serverId) {
        SshSession session = sessionManager.acquire(serverId);
        JSONObject remote = readRemoteConfig(session);
        List<XrayClientDO> rows = xrayClientMapper.selectByServerId(serverId);
        Map<String, List<XrayClientDO>> byInboundTag = rows.stream()
                .filter(r -> StrUtil.isNotBlank(r.getExternalInboundRef()))
                .collect(Collectors.groupingBy(XrayClientDO::getExternalInboundRef));

        inboundConfigReconciler.repopulateClients(remote, byInboundTag);
        remote.put("outbounds", outboundConfigReconciler.buildOutbounds(rows));
        remote.put("routing", routingConfigReconciler.buildRouting(rows));

        String json = remote.toJSONString(JSONWriter.Feature.PrettyFormat,
                JSONWriter.Feature.WriteMapNullValue);
        pushAndReload(session, json);
        log.info("[config-sync] OK server={} clients={} bytes={}", serverId, rows.size(), json.length());
    }

    // ===== 远端配置读取 =====

    /** SSH 拉远端 xray.json 并解析; 空文件 / 解析失败抛 BACKEND_RESPONSE_INVALID. */
    private JSONObject readRemoteConfig(SshSession session) {
        String json = session.ssh().exec("cat " + RemoteFiles.CONFIG_PATH, OP_TIMEOUT).stdout();
        if (StrUtil.isBlank(json)) {
            throw new BusinessException(XrayErrorCode.BACKEND_RESPONSE_INVALID,
                    session.serverId(), "远端 xray.json 为空");
        }
        try {
            JSONObject root = JSONObject.parseObject(json);
            if (ObjectUtil.isNull(root)) {
                throw new BusinessException(XrayErrorCode.BACKEND_RESPONSE_INVALID,
                        session.serverId(), "远端 xray.json 解析为空");
            }
            return root;
        } catch (JSONException e) {
            throw new BusinessException(XrayErrorCode.BACKEND_RESPONSE_INVALID, e,
                    session.serverId(), "远端 xray.json 非法: " + e.getMessage());
        }
    }

    // ===== 上传 + reload =====

    /** 上传 → chmod → xray test → 原子 mv → systemctl restart → is-active; 任一步失败清理 tmp 后抛错. */
    private void pushAndReload(SshSession session, String json) {
        String remoteTmp = RemoteFiles.TMP_UPLOAD_PREFIX + System.currentTimeMillis() + ".json";
        session.ssh().uploadString(remoteTmp, json, OP_TIMEOUT);
        // 一条 SSH 命令打通: chmod / 校验 / 原子 mv / restart / 等启动完成; 任一失败 abort, 残留 tmp 由 catch 清理
        String cmd = String.join(" && ",
                "chmod 640 '" + remoteTmp + "'",
                "chown root:" + RemoteFiles.SYSTEMD_UNIT + " '" + remoteTmp + "' 2>/dev/null || true",
                // 新版 Xray 用 'run -test', 老版兼容 'test'
                "(xray run -test -c '" + remoteTmp + "' >/dev/null 2>&1"
                        + " || xray test -c '" + remoteTmp + "' >/dev/null 2>&1)",
                "mv '" + remoteTmp + "' '" + RemoteFiles.CONFIG_PATH + "'",
                "systemctl restart " + RemoteFiles.SYSTEMD_UNIT,
                "sleep 1",
                "systemctl is-active " + RemoteFiles.SYSTEMD_UNIT
        );
        try {
            session.ssh().exec(cmd, OP_TIMEOUT);
        } catch (BusinessException be) {
            log.error("[config-sync] 远端 reload 失败 server={}", session.serverId(), be);
            cleanupQuietly(session, remoteTmp);
            throw be;
        }
    }

    /** 残留临时文件清理; 静默吞错 — 我们不希望它把上层真正的失败原因覆盖掉. */
    private void cleanupQuietly(SshSession session, String remoteTmp) {
        try {
            session.ssh().exec("rm -f '" + remoteTmp + "'", Duration.ofSeconds(10));
        } catch (RuntimeException cleanupErr) {
            log.warn("[config-sync] 清理临时文件失败 server={} path={}",
                    session.serverId(), remoteTmp, cleanupErr);
        }
    }
}
