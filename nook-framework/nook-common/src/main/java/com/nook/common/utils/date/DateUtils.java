package com.nook.common.utils.date;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期 / 时间工具类
 *
 * @author nook
 */
public class DateUtils {

    private DateUtils() {}

    /**
     * 解析含时区偏移的时间字符串到 LocalDateTime, 失败返回 fallback.
     *
     * <p>典型用法: 远端 shell `date -d` 重格式化为 "yyyy-MM-dd HH:mm:ss Z" 后, backend 解析回 LocalDateTime 落库.
     *
     * @param raw       待解析字符串 (空白 / null / 格式不匹配都走 fallback)
     * @param formatter 输入格式 (必须能解出 zone offset, 否则用 {@link LocalDateTime#parse(CharSequence, DateTimeFormatter)})
     * @param fallback  解析失败时使用的默认值
     * @return 解析成功的 LocalDateTime, 或 fallback
     */
    public static LocalDateTime parseOffsetOrFallback(String raw, DateTimeFormatter formatter, LocalDateTime fallback) {
        if (StrUtil.isBlank(raw)) return fallback;
        try {
            return OffsetDateTime.parse(raw.trim(), formatter).toLocalDateTime();
        } catch (Exception e) {
            return fallback;
        }
    }
}
