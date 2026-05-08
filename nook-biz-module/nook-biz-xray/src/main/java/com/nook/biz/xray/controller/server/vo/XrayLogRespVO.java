package com.nook.biz.xray.controller.server.vo;

import lombok.Data;

/**
 * Xray journalctl 日志快照; 由 ServerInspector 按行数 / 级别拉取。
 */
@Data
public class XrayLogRespVO {

    /** 实际请求的行数 (server 端会 clamp 到合理上限) */
    private int lines;

    /** all / warning / err 之一 */
    private String level;

    /** journalctl 完整输出 */
    private String log;
}
