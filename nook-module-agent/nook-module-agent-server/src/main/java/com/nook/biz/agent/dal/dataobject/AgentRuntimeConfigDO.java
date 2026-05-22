package com.nook.biz.agent.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Agent 运行时配置: 每台 server 一份 yaml, admin 改后派 config_reload task. */
@Data
@TableName("agent_runtime_config")
public class AgentRuntimeConfigDO {

    /** FK → resource_server.id; PK. */
    @TableId
    private String serverId;

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
