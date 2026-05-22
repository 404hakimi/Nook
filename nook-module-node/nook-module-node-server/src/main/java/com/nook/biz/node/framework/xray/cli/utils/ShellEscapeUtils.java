package com.nook.biz.node.framework.xray.cli.utils;

/**
 * Shell 字符串转义工具; 用单引号方案 ($ / ` / \ / " 都不展开, 安全边界小).
 *
 * @author nook
 */
public final class ShellEscapeUtils {

    private ShellEscapeUtils() {
    }

    /**
     * 转义可放入 shell 单引号字符串内, 单引号本身用 '\'' 转义; 不含外层单引号.
     *
     * @param s 原始字符串
     * @return 转义后内容
     */
    public static String singleQuoteContent(String s) {
        if (s == null) return "";
        return s.replace("'", "'\\''");
    }

    /**
     * 包成完整的 shell 单引号参数 (含外层 quote), 拼到命令行可直接当一个 token.
     *
     * @param s 原始字符串
     * @return shell 安全 token
     */
    public static String shellArg(String s) {
        return "'" + singleQuoteContent(s) + "'";
    }
}
