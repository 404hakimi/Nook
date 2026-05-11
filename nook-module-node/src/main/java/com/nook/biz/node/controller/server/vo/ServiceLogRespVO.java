package com.nook.biz.node.controller.server.vo;

import lombok.Data;

/**
 * 指定 systemd unit 的 journalctl 日志快照; 由 ServerInspector 按 unit / 行数 / 级别拉取。
 */
@Data
public class ServiceLogRespVO {

    /** 查询的 systemd unit 名 (如 xray, sshd, nginx 等); 与请求入参一致 */
    private String unit;

    /** 实际请求的行数 (server 端会 clamp 到合理上限) */
    private int lines;

    /** all / warning / err 之一 */
    private String level;

    /** journalctl 完整输出 */
    private String log;
}
