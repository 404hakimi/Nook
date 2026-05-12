package com.nook.biz.node.controller.resource.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.node.enums.ResourceServerStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器列表 / 详情 Response VO.
 *
 * @author nook
 */
@Data
public class ResourceServerRespVO {

    private String id;
    private String name;
    private String host;
    private Integer sshPort;
    private String sshUser;

    /** 明文 SSH 密码; 后台运营受信网络下使用, UI 用 type=password 自然遮盖, 编辑时直接 fill 回密码框. */
    private String sshPassword;

    private Integer sshTimeoutSeconds;
    private Integer sshOpTimeoutSeconds;
    private Integer sshUploadTimeoutSeconds;
    private Integer installTimeoutSeconds;

    private Integer totalBandwidth;
    private Integer monthlyTrafficGb;
    private Integer totalIpCount;
    private String idcProvider;
    private String region;

    /** 状态; 取值见 {@link ResourceServerStatusEnum} */
    private Integer status;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
