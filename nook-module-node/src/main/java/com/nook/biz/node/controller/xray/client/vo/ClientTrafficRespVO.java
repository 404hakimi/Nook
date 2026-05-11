package com.nook.biz.node.controller.xray.client.vo;

import lombok.Data;

/**
 * 客户端实时流量出参; 字节原值 + 人读字符串都下发, 前端 0 计算 (展示直接用 *Text, 比较/排序仍可用 *Bytes).
 */
@Data
public class ClientTrafficRespVO {

    private String inboundEntityId;
    private String clientEmail;

    /** 上行字节数 (精确数值, 给排序 / 排行用); 展示请用 upBytesText. */
    private long upBytes;
    /** 上行人读字符串, 例 "190.50 KB". */
    private String upBytesText;

    /** 下行字节数; 展示请用 downBytesText. */
    private long downBytes;
    /** 下行人读字符串, 例 "48.26 MB". */
    private String downBytesText;

    /** 累计已用 = upBytes + downBytes; 展示请用 usedBytesText. */
    private long usedBytes;
    /** 累计已用人读字符串, 例 "48.45 MB". */
    private String usedBytesText;

    /** 流量上限(字节); 0 = 不限. */
    private long totalBytes;
    /** 流量上限人读字符串; 0 时返 "无限制" 让前端无需额外判断. */
    private String totalBytesText;

    /**
     * 用量百分比 (0-100); totalBytes=0 时返 null 表示"不适用",
     * 让前端 v-if 直接判断要不要画进度条, 不用再看 totalBytes 数值.
     */
    private Integer usagePct;

    /** 到期时间(毫秒); 0 = 永久. */
    private long expiryEpochMillis;
    private boolean enabled;
}
