package com.nook.biz.node.controller.resource.vo.ops;

import lombok.Data;

/**
 * 管理后台 - Systemd 服务状态 Response VO
 *
 * @author nook
 */
@Data
public class SystemdStatusRespVO {

    /** 查询的 systemd unit 名 (如 xray, sshd, nginx 等); 与请求入参一致 */
    private String unit;

    /** systemctl is-active 输出: active / inactive / failed / unknown */
    private String active;

    /** ActiveEnterTimestamp, 如 "Wed 2026-05-08 12:30:11 UTC"; 服务未起时为空 */
    private String uptimeFrom;

    /** systemctl is-enabled 输出: enabled / disabled / static / masked / ... */
    private String enabled;
}
