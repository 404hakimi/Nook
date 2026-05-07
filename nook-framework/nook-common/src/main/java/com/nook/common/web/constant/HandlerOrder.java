package com.nook.common.web.constant;

/**
 * 全局处理器执行顺序常量；值越小优先级越高，统一在此管理避免散落。
 * 新增 @RestControllerAdvice / 拦截器 / Filter 时必须从这里取值，禁止硬编码 @Order(数字)。
 */
public final class HandlerOrder {

    private HandlerOrder() {
    }

    // ========== 异常处理器 (@RestControllerAdvice) ==========

    /** sa-token 鉴权异常处理器；最高优先级，必须在通用异常之前抢先匹配。 */
    public static final int SA_TOKEN_EXCEPTION = 100;

    /** 业务异常专用处理器（预留：现阶段统一在 GlobalExceptionHandler 中处理）。 */
    public static final int BUSINESS_EXCEPTION = 500;

    /** 通用兜底异常处理器；最低优先级，捕获所有未被前面命中的 Throwable。 */
    public static final int GLOBAL_EXCEPTION = Integer.MAX_VALUE - 100;

    // ========== 拦截器 / Filter (待补) ==========

    // public static final int RATE_LIMIT_INTERCEPTOR = 200;
    // public static final int AUDIT_LOG_INTERCEPTOR = 300;
}
