package com.nook.biz.resource.controller.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器详情/列表响应.
 *
 * <p>SSH 密码以明文下发 — DB 本就明文存, 后台运营在受信网络下使用,
 * 编辑时直接 fill 进密码框 (type=password, UI 自然遮盖).
 * <p>Xray 配置 (gRPC port / slot 池等) 不在本响应里, 走 xray_node 接口单独取.
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

    private Integer status;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
