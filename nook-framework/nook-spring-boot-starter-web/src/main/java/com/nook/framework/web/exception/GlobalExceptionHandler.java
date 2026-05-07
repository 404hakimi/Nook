package com.nook.framework.web.exception;

import com.nook.common.web.constant.HandlerOrder;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/** 全局异常处理器，把所有 Controller 抛出的异常统一转为 Result。 */
@Slf4j
@Order(HandlerOrder.GLOBAL_EXCEPTION)
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按错误码原样返回，记 WARN（非系统问题）。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest req) {
        log.warn("[业务异常] uri={} code={} message={}", req.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @Valid / @Validated 在 @RequestBody 上的校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(CommonErrorCode.PARAM_INVALID, msg);
    }

    /** @Valid / @Validated 在表单/查询参数对象上的校验失败。 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(CommonErrorCode.PARAM_INVALID, msg);
    }

    /** 直接标注在 @RequestParam / @PathVariable 上的约束(如 @NotBlank)失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(this::formatViolation)
                .collect(Collectors.joining("; "));
        return Result.fail(CommonErrorCode.PARAM_INVALID, msg);
    }

    /** 缺少必填请求参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(CommonErrorCode.PARAM_INVALID, "缺少参数: " + e.getParameterName());
    }

    /** 请求体格式错误(如 JSON 解析失败)。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(CommonErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    /** 请求方法不支持。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 路由不存在(需开启 spring.mvc.throw-exception-if-no-handler-found = true)。 */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        return Result.fail(CommonErrorCode.NOT_FOUND);
    }

    /** 兜底：未预期的系统异常，打 ERROR 并附完整堆栈。 */
    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleThrowable(Throwable e, HttpServletRequest req) {
        log.error("[系统异常] uri={}", req.getRequestURI(), e);
        return Result.fail(CommonErrorCode.INTERNAL_ERROR);
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    private String formatViolation(ConstraintViolation<?> v) {
        return v.getPropertyPath() + ": " + v.getMessage();
    }
}
