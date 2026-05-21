package com.nook.biz.node.controller.agent.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Admin 保存 yaml 入参; 直接整段覆盖. */
@Data
public class AgentRuntimeConfigSaveReqVO {

    @NotBlank(message = "yaml 不能为空")
    @Size(max = 100_000, message = "yaml 过长 (>100KB), 配置文件不该这么大")
    private String configYaml;
}
