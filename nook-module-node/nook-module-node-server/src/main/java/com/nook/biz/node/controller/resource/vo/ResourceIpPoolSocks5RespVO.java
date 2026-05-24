package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - IP 池 dante 配置 Response VO
 *
 * <p>实际限速字段拆到 capacity 子表 + CapacityRespVO.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolSocks5RespVO {

    private String ipId;
    private Integer socks5Port;
    private String socks5Username;

    /** 明文 SOCKS5 密码; 后台运营受信网络下使用, UI 用 type=password 自然遮盖. */
    private String socks5Password;
    private String logLevel;
}
