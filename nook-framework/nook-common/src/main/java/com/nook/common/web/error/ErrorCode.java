package com.nook.common.web.error;

/** 错误码契约，由各模块的枚举实现；段位划分见后端开发规范 §4.2。 */
public interface ErrorCode {

    /** 数字错误码。0=成功；其它见各模块定义。 */
    int getCode();

    /** 错误消息模板，可含 String.format 占位符。 */
    String getMessage();

    /** 用运行时参数格式化错误消息。 */
    default String format(Object... args) {
        // 无参时直接返回，避免 String.format 为空 args 也走一次格式化
        if (args == null || args.length == 0) {
            return getMessage();
        }
        return String.format(getMessage(), args);
    }
}
