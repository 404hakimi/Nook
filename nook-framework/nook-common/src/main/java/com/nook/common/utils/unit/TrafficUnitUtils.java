package com.nook.common.utils.unit;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 流量 / 字节单位换算工具.
 *
 * <p>统一用 BigDecimal + HALF_UP 保留 2 位小数, 避免浮点误差; 系统内流量换算 (展示 / 阈值) 都走这里, 不要再自己写 1024 计算.
 *
 * @author nook
 */
public final class TrafficUnitUtils {

    private TrafficUnitUtils() {}

    /** 展示保留小数位. */
    private static final int SCALE = 2;
    private static final long KB = 1024L;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    /** 字节 → MB, 保留 2 位小数. */
    public static BigDecimal toMb(long bytes) {
        return scale(bytes, MB);
    }

    /** 字节 → GB, 保留 2 位小数. */
    public static BigDecimal toGb(long bytes) {
        return scale(bytes, GB);
    }

    /** GB → 字节; 把套餐 / 配额的 GB 阈值换算成字节做比较. */
    public static long gbToBytes(long gb) {
        return gb * GB;
    }

    /** 字节 → 自动单位的可读字符串 (B/KB/MB/GB/TB), 保留 2 位小数, 单位前留空格 (如 "1.50 MB"); 0/负数返 "0 B". */
    public static String humanize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        int i = 0;
        long unit = 1L;
        while (i < UNITS.length - 1 && bytes >= unit * 1024) {
            unit *= 1024;
            i++;
        }
        return scale(bytes, unit).toPlainString() + " " + UNITS[i];
    }

    /** bytes / unit, 保留 SCALE 位小数 (HALF_UP). */
    private static BigDecimal scale(long bytes, long unit) {
        return BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(unit), SCALE, RoundingMode.HALF_UP);
    }
}
