package com.nook.biz.node.framework.server.snapshot;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 指定 systemd unit 的运行状态投影; 通用, 不掺任何 service 专属字段.
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class SystemdStatusSnapshot {

    /** systemd unit 名. */
    private String unit;

    /** is-active 输出 (active / inactive / failed / activating ...). */
    private String active;

    /** 启动时间 (ActiveEnterTimestamp 原文). */
    private String uptimeFrom;

    /** is-enabled 输出 (enabled / disabled / static / masked ...). */
    private String enabled;
}
