package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/** 指定 systemd unit 的通用运行状态; 给 ServerInspector 通用查询用 (sshd / nginx / xray 等任意 unit). */
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
