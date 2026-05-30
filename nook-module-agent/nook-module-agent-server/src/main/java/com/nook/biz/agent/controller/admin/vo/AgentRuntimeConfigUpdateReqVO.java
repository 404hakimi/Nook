package com.nook.biz.agent.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - Agent 运行时配置更新 Request VO
 *
 * @author nook
 */
@Data
public class AgentRuntimeConfigUpdateReqVO {

    /** 完整 yaml 配置内容. */
    @NotBlank(message = "yaml 不能为空")
    @Size(max = 100_000, message = "yaml 过长 (>100KB), 配置文件不该这么大")
    private String configYaml;
}
