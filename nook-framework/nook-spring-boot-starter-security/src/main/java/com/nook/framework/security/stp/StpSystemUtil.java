package com.nook.framework.security.stp;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 后台用户的 sa-token 工具类，loginType="system"，与会员体系隔离。
 * 业务代码统一通过本类访问 sa-token，不要直接用 StpUtil。
 */
public final class StpSystemUtil {

    /** loginType 在 sa-token 内部用于区分账号体系；不要改，否则会与已有 token 不兼容。 */
    public static final String LOGIN_TYPE = "system";

    private static final StpLogic STP_LOGIC = new StpLogic(LOGIN_TYPE);

    private StpSystemUtil() {
    }

    /** 暴露 StpLogic 给 SaTokenConfig 注册路由校验。 */
    public static StpLogic stpLogic() {
        return STP_LOGIC;
    }

    /** 登录指定后台用户，自动签发 token 并写 Redis。 */
    public static void login(Object userId) {
        STP_LOGIC.login(userId);
    }

    /** 当前请求的 token 原值。 */
    public static String getTokenValue() {
        return STP_LOGIC.getTokenValue();
    }

    /** token 剩余有效期(秒)；-1=永久，-2=不存在。 */
    public static long getTokenTimeout() {
        return STP_LOGIC.getTokenTimeout();
    }

    /** 当前登录用户 ID(字符串形式)。 */
    public static String getLoginIdAsString() {
        return STP_LOGIC.getLoginIdAsString();
    }

    /** 当前请求是否已登录。 */
    public static boolean isLogin() {
        return STP_LOGIC.isLogin();
    }

    /** 强校验登录态；未登录抛 NotLoginException 由全局处理。 */
    public static void checkLogin() {
        STP_LOGIC.checkLogin();
    }

    /** 注销当前 token；幂等。 */
    public static void logout() {
        STP_LOGIC.logout();
    }
}
