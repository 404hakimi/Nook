package com.nook.common.web.exception;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;

import java.io.Serial;

/** 业务异常，由 GlobalExceptionHandler 统一转为 Result 返回。 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    /** 抛指定错误码，使用枚举默认 message。 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 抛指定错误码，用 args 格式化 message 中的 %s 占位。 */
    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.format(args));
        this.code = errorCode.getCode();
    }

    /** 抛指定错误码，并保留下层 cause 的堆栈。 */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
    }

    /** 抛指定错误码 + 格式化参数 + 保留下层 cause；外部依赖异常包装时常用。 */
    public BusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.format(args), cause);
        this.code = errorCode.getCode();
    }

    /** 直接给 code+message，兜底场景；业务代码优先使用 ErrorCode 枚举。 */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** code+message + 下层 cause。 */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 抑制堆栈生成。 */
    @Override
    public synchronized Throwable fillInStackTrace() {
        // 业务异常承载的是预期错误(参数无效 / 状态冲突等)，无须付出生成堆栈的开销；
        // 真正未预期的系统异常走原生 RuntimeException 路径，堆栈仍正常生成。
        return this;
    }
}
