package com.nook.biz.agent.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.agent.api.enums.AgentRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 运行时配置 DO
 *
 * @author nook
 */
@Data
@TableName("agent_runtime_config")
public class AgentRuntimeConfigDO {

    /** 服务器 id (主键). */
    @TableId
    private String serverId;

    /** agent 角色 {@link AgentRole} */
    private String agentType;

    /** 完整 yaml 内容. */
    private String configYaml;

    /** 更新时间. */
    private LocalDateTime updatedAt;

    /** 操作人(admin)编号. */
    private String updatedBy;

    /** Agent 应用时间; null=从未. */
    private LocalDateTime appliedAt;

    /** Agent 已应用 yaml 的 MD5. */
    private String appliedYamlMd5;
}
