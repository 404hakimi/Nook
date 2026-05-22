package com.nook.biz.node.convert.xray.format;

/**
 * 字节数格式化工具
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
