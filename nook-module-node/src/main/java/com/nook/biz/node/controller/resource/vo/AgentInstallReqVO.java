package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Agent SSH 装机请求; 字段不入库, 仅本次装机用.
 *
 * <p>configYaml 是用户在 dialog 里编辑的完整 agent yaml; 必须包含字面量 {{AGENT_TOKEN}} 占位符,
 * backend 装机时把新 token 拼进去再 SSH 写到远端 /home/nook-agent/etc/config.yml.
 *
 * @author nook
 */
@Data
public class AgentInstallReqVO {

    /** frontline (跑 xray) / landing (跑 socks5). */
    @NotBlank(message = "role 不能为空")
    @Pattern(regexp = "frontline|landing", message = "role 只能是 frontline / landing")
    private String role;

    /** 完整 agent 配置 yaml; 必须含 {{AGENT_TOKEN}} 占位符. */
    @NotBlank(message = "configYaml 不能为空")
    private String configYaml;
}
