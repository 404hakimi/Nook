package com.nook.biz.node.framework.xray.cli.snapshot;

import lombok.Builder;
import lombok.Data;

/**
 * Xray stats CLI 读取的用户流量瞬时快照, 字节计数; totalBytes / expiry / enabled 在 nook 模式由业务侧维护.
 *
 * @author nook
 */
@Data
@Builder
public class XrayUserTrafficSnapshot {

    /** 用户 email. */
    private String email;

    /** 累计上行字节数. */
    private long upBytes;

    /** 累计下行字节数. */
    private long downBytes;

    /** 流量上限字节数; nook 模式 0 表无限制. */
    private long totalBytes;

    /** 到期 epoch millis; nook 模式 0 表永不到期. */
    private long expiryEpochMillis;

    /** 是否启用; nook 模式由业务侧维护. */
    private boolean enabled;
}
