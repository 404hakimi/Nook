package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理后台 - Xray inbound 共享配置 Response VO
 *
 * @author nook
 */
@Data
public class XrayInboundRespVO {

    /** 服务器编号. */
    private String serverId;

    /** 共享 inbound 协议判别键 (vmess / vless). */
    private String protocol;

    /** 共享 inbound 监听端口. */
    private Integer sharedInboundPort;

    /** 协议字段值 (key = 该协议 formSchema 字段 name; vmess: wsPath/domainId/subdomain, vless: realityDest); 重装预填用. */
    private Map<String, Object> formValues;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
