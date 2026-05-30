package com.nook.biz.agent.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.agent.api.enums.AgentConfigSyncState;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Agent 运行时配置 Response VO
 *
 * @author nook
 */
@Data
public class AgentRuntimeConfigRespVO {

    /** 所属 server 编号. */
    private String serverId;

    /** 完整 yaml; null = 没配过 (agent 用装机脚本写的本地 yaml). */
    private String configYaml;

    /** 更新时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 操作人(admin)编号. */
    private String updatedBy;

    /** Agent 上次应用时间; null = 从未. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appliedAt;

    /** Agent 已应用 yaml 的 md5. */
    private String appliedYamlMd5;

    /** 同步状态 {@link AgentConfigSyncState} */
    private String syncState;
}
