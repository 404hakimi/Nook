package com.nook.framework.security.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.nook.common.web.constant.HandlerOrder;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** sa-token 鉴权异常映射为 Result，优先级高于 GlobalExceptionHandler 抢先匹配。 */
@Slf4j
@Order(HandlerOrder.SA_TOKEN_EXCEPTION)
@RestControllerAdvice
public class SaTokenExceptionHandler {

    /** 未登录或 token 过期。 */
    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(NotLoginException e) {
        // sa-token 的 type 区分了具体原因(token 不存在/过期/被踢/无效等)，统一返回 401
        return Result.fail(CommonErrorCode.UNAUTHORIZED);
    }

    /** 缺少角色。 */
    @ExceptionHandler(NotRoleException.class)
    public Result<Void> handleNotRole(NotRoleException e) {
        return Result.fail(CommonErrorCode.FORBIDDEN);
    }

    /** 缺少权限。 */
    @ExceptionHandler(NotPermissionException.class)
    public Result<Void> handleNotPermission(NotPermissionException e) {
        return Result.fail(CommonErrorCode.FORBIDDEN);
    }
}
