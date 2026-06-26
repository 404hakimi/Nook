package com.nook.biz.node.framework.agent;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.framework.socks5.install.Socks5DeployRequest;
import com.nook.biz.node.framework.xray.install.XrayCertPushRequest;
import com.nook.biz.node.framework.xray.install.XrayDeployRequest;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * agent 控制接口客户端; 后台 POST agent:44844 (/xray/deploy 下发装机 / /xray/cert 下发续期证书), 流式读回 stdout.
 * body 经 AES-GCM 加密 (AgentControlCrypto), token 不过线; agent 端解密即鉴权.
 *
 * @author nook
 */
@Slf4j
@Component
public class AgentControlClient {

    /** agent 控制接口固定端口; 装机时 UFW 放行给后台出口 IP. */
    private static final int CONTROL_PORT = 44844;
    /** 远端执行成功的机器可读结尾标记, 跟 nook-agent control 包对齐. */
    private static final String MARK_OK = "NOOK_RESULT=ok";
    /** TCP 建链超时秒数; 后台与节点跨洲互通 (如后台北美 ↔ 亚洲日本节点), 给国际链路 SYN 丢包/抖动留足余量. */
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    /** HTTP 响应超时 = 脚本执行超时 + 此缓冲; 缓冲覆盖跨洲流式回传的延迟/抖动. 须 < SSE emitter buffer (见 WebStreamingProperties), 否则外层 SSE 先断. */
    private static final int RESPONSE_BUFFER_SECONDS = 120;

    /**
     * 通知 agent 用内置逻辑本地装机 xray; 下发结构化配置 (非脚本), 流式回传安装日志, 未见成功标记则抛错
     *
     * @param agentHost agent 主机 (线路机出网 IP)
     * @param token     agent 鉴权 token (resource_server.agent_token)
     * @param req       装机请求 (版本/开关/inbound JSON/域名; 内含超时)
     * @param lineSink  安装日志行回调 (转发给前端流式)
     */
    public void deployXray(String agentHost, String token, XrayDeployRequest req, Consumer<String> lineSink) {
        this.postStreaming(agentHost, "/xray/deploy", token, JSON.toJSONString(req), req.getTimeoutSeconds(), lineSink);
    }

    /**
     * 通知 agent 写入续期后的新证书并 reload xray (轻量, 不重走装机); 复用 /xray/cert 流式约定
     *
     * @param agentHost agent 主机 (线路机出网 IP)
     * @param token     agent 鉴权 token
     * @param req       证书下发请求 (域名 + cert/key + 超时)
     * @param lineSink  日志行回调
     */
    public void pushCert(String agentHost, String token, XrayCertPushRequest req, Consumer<String> lineSink) {
        this.postStreaming(agentHost, "/xray/cert", token, JSON.toJSONString(req), req.getTimeoutSeconds(), lineSink);
    }

    /**
     * 通知 landing agent 用内置逻辑本地装 dante; 下发结构化期望态 (非脚本), 流式回传安装日志, 未见成功标记则抛错
     *
     * @param agentHost agent 主机 (落地机出网 IP)
     * @param token     agent 鉴权 token (resource_server.agent_token)
     * @param req       SOCKS5 装机请求 (端口/账密/路径/开关; 内含超时)
     * @param lineSink  安装日志行回调 (转发给前端流式)
     */
    public void deploySocks5(String agentHost, String token, Socks5DeployRequest req, Consumer<String> lineSink) {
        this.postStreaming(agentHost, "/socks5/deploy", token, JSON.toJSONString(req), req.getTimeoutSeconds(), lineSink);
    }

    /** POST agent 控制接口 + 流式读回 stdout; 非 200 / 无成功标记 / IO 中断均抛 BusinessException. */
    private void postStreaming(String agentHost, String path, String token, String bodyJson,
                               int timeoutSeconds, Consumer<String> lineSink) {
        String url = "http://" + agentHost + ":" + CONTROL_PORT + path;
        // 通道是明文 HTTP 且 body 含 TLS 私钥 → AES-256-GCM 端到端加密 (key 由 agent_token 派生).
        // token 不再放任何请求头 (否则窃听者抓头即可算出 key); agent「能解密」即鉴权 (见 AgentControlCrypto).
        String encryptedBody = AgentControlCrypto.encrypt(bodyJson, token);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/octet-stream")
                .header(AgentControlCrypto.ENC_HEADER, AgentControlCrypto.ENC_VALUE)
                .timeout(Duration.ofSeconds(timeoutSeconds + RESPONSE_BUFFER_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(encryptedBody, StandardCharsets.UTF_8))
                .build();
        boolean ok;
        try {
            HttpResponse<Stream<String>> resp = client.send(req, HttpResponse.BodyHandlers.ofLines());
            if (resp.statusCode() != 200) {
                String errBody = resp.body().limit(5).collect(Collectors.joining("; "));
                throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, agentHost,
                        "agent 控制接口 HTTP " + resp.statusCode() + ": " + StrUtil.maxLength(errBody, 200));
            }
            ok = this.relayLines(resp.body(), lineSink);
        } catch (IOException e) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, e, agentHost,
                    "调 agent 控制接口失败: " + StrUtil.maxLength(e.getMessage(), 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, e, agentHost, "调 agent 控制接口被中断");
        }
        if (!ok) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, agentHost,
                    "agent 未确认成功 (无 NOOK_RESULT=ok 标记; 可能超时或连接中断, 远端可能已部分执行, 重新部署幂等修复)");
        }
        log.info("[agent-control] {} server={} 成功", path, agentHost);
    }

    /** 流式转发每行给 lineSink, 返回是否见到成功结尾标记. */
    private boolean relayLines(Stream<String> lines, Consumer<String> lineSink) {
        boolean[] ok = {false};
        lines.forEach(line -> {
            lineSink.accept(line + "\n");
            if (line.contains(MARK_OK)) {
                ok[0] = true;
            }
        });
        return ok[0];
    }
}
