package com.nook.biz.agent.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** Admin 拿某 server 的 agent 运行时配置 + 同步状态. */
@Data
public class AgentRuntimeConfigRespVO {

    private String serverId;

    /** 完整 yaml; null = 没配过 (agent 用装机脚本写的本地 yaml). */
    private String configYaml;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private String updatedBy;

    /** Agent 上次应用时间; null = 从未. updatedAt > appliedAt 说明 admin 改了 agent 还没拿到. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appliedAt;

    /** Agent 应用的 yaml md5; 跟 config_yaml 算出来的 md5 比对, 一致 → SYNCED. */
    private String appliedYamlMd5;

    /** 同步状态: NEVER_CONFIGURED / SYNCED / PENDING. UI 一眼看. */
    private String syncState;
}
