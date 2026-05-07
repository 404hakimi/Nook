package com.nook.common.web.response;

import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.error.ErrorCode;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** Controller 统一返回结构: { code, message, data }。 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    /** 成功无返回数据。 */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /** 成功并携带数据。 */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = CommonErrorCode.SUCCESS.getCode();
        r.message = CommonErrorCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    /** 失败：直接给 code+message，业务代码优先用错误码枚举重载。 */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /** 失败：使用错误码枚举的默认 message。 */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    /** 失败：用 args 格式化错误码 message 中的占位符。 */
    public static <T> Result<T> fail(ErrorCode errorCode, Object... args) {
        return fail(errorCode.getCode(), errorCode.format(args));
    }
}
