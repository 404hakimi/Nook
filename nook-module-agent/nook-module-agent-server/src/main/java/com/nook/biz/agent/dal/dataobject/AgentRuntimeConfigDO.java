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

    /** 完整 yaml 内容; agent 直接 mv 到 /etc/nook-agent/config.yml. */
    private String configYaml;

    /** Admin 改动时间; agent 拿这跟 lastAppliedAt 比. */
    private LocalDateTime updatedAt;

    /** admin user.id. */
    private String updatedBy;

    /** Agent 应用时间; null=从未. */
    private LocalDateTime appliedAt;

    /** Agent 应用的 yaml MD5; 用来判断 yaml 内容是否真同步 (防 admin 改了但 agent 拿到的是旧的). */
    private String appliedYamlMd5;
}
