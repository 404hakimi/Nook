package com.nook.biz.node.framework.agent;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayErrorCode;
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
 * agent 控制接口客户端; 后台 POST agent:44844/execute 下发脚本, 流式读回 agent 本地执行的 stdout
 *
 * @author nook
 */
@Slf4j
@Component
public class AgentControlClient {

    /** agent 控制接口固定端口; 装机时 UFW 放行给后台出口 IP. */
    private static final int CONTROL_PORT = 44844;
    private static final String TOKEN_HEADER = "X-Agent-Token";
    /** 远端执行成功的机器可读结尾标记, 跟 nook-agent control 包对齐. */
    private static final String MARK_OK = "NOOK_RESULT=ok";

    /**
     * 调 agent 本地执行脚本; 流式回传 stdout, 未见成功标记则抛错
     *
     * @param agentHost      agent 主机 (线路机出网 IP)
     * @param token          agent 鉴权 token (resource_server.agent_token)
     * @param script         完整脚本文本
     * @param timeoutSeconds 执行超时秒数
     * @param lineSink       stdout 行回调 (转发给前端流式)
     */
    public void execute(String agentHost, String token, String script, int timeoutSeconds, Consumer<String> lineSink) {
        String url = "http://" + agentHost + ":" + CONTROL_PORT + "/execute";
        JSONObject body = new JSONObject();
        body.put("script", script);
        body.put("timeoutSeconds", timeoutSeconds);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header(TOKEN_HEADER, token)
                .timeout(Duration.ofSeconds(timeoutSeconds + 30L))
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8))
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
        log.info("[agent-control] execute server={} 远端执行成功", agentHost);
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
