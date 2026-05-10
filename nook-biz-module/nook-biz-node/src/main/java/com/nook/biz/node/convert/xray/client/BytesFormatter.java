package com.nook.biz.node.convert.xray.client;

/**
 * 字节数 → 人读字符串格式化; 与前端旧 fmtBytes 规则对齐, 出参展示从前端搬到后端后保持视觉无差异.
 *
 * <p>规则: 0 / 负 → "0 B"; ≥1024 进位走 KB / MB / GB / TB, 保留 2 位小数, 单位之间空格分隔.
 *
 * @author nook
 */
public final class BytesFormatter {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private BytesFormatter() {
    }

    /**
     * 字节数 → 人读字符串.
     *
     * @param bytes 字节数 (允许 0 / 负数)
     * @return 例如 "1.00 KB" / "1.50 MB" / "0 B"
     */
    public static String human(long bytes) {
        if (bytes <= 0) return "0 B";
        double v = bytes;
        int i = 0;
        while (v >= 1024 && i < UNITS.length - 1) {
            v /= 1024;
            i++;
        }
        // %.2f 保留两位小数; 单位前留空格让"190.50 KB"易读, 与前端 fmtBytes 输出一致
        return String.format("%.2f %s", v, UNITS[i]);
    }
}
