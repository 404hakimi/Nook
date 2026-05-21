package com.nook.biz.node.controller.agent.admin.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 一键清日志入参; agent 端有 allowedRoots 白名单, 二次校验. */
@Data
public class AdminTruncateLogReqVO {

    /**
     * 要 truncate 的文件绝对路径; 必须以 /var/log/ 或 /home/socks5/logs/ 或 /home/xray/logs/ 开头.
     * 安全防御: VO 层 + agent 端双校验.
     */
    @NotEmpty(message = "paths 不能为空")
    @Size(max = 50, message = "paths 最多 50 条")
    private List<@Pattern(regexp = "^/(var/log|home/socks5/logs|home/xray/logs)/.*",
            message = "路径必须落在 /var/log / /home/socks5/logs / /home/xray/logs 下") String> paths;
}
