package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 装机事实 Update Request VO
 *
 * <p>所有字段强制必填; 默认值由前端 form 填好 (后端不再 blankToDefault 兜底).
 *
 * @author nook
 */
@Data
public class ResourceIpPoolInstallUpdateReqVO {

    @NotBlank(message = "installDir 必填")
    @Size(max = 255)
    private String installDir;

    @NotBlank(message = "logPath 必填")
    @Size(max = 255)
    private String logPath;

    @NotBlank(message = "confPath 必填; dante 主配置文件绝对路径")
    @Size(max = 255)
    private String confPath;

    @NotBlank(message = "pamFile 必填")
    @Size(max = 255)
    private String pamFile;

    @NotBlank(message = "pwdFile 必填")
    @Size(max = 255)
    private String pwdFile;

    @NotBlank(message = "systemdUnit 必填")
    @Size(max = 64)
    private String systemdUnit;

    @NotNull(message = "autostartEnabled 必填")
    @Min(value = 0) @Max(value = 1)
    private Integer autostartEnabled;

    @NotNull(message = "firewallEnabled 必填")
    @Min(value = 0) @Max(value = 1)
    private Integer firewallEnabled;

    @NotNull(message = "logRotateEnabled 必填")
    @Min(value = 0) @Max(value = 1)
    private Integer logRotateEnabled;
}
