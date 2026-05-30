package com.nook.biz.agent.framework.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 模块配置项
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.agent")
public class AgentProperties {

    /** Agent binary 所在目录; 相对路径以 backend 启动 CWD 为基准. */
    @NotBlank
    private String binDir = "agent";

    /** Agent 源码根目录 (相对 backend CWD); 装机时 admin 走 /agent-dist/download-agent-src 流式打包下发. */
    @NotBlank
    private String srcDir = "nook-agent";

    /** Backend 公网 URL; agent 装机后回拉 binary / 心跳 / 升级都用这个根地址. 部署侧通过 NOOK_BACKEND_PUBLIC_URL 注入. */
    private String backendPublicUrl = "";
}
