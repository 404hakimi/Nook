package com.nook.framework.security.stp;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 会员的 sa-token 工具类，loginType="member"，与后台体系隔离。
 * 现阶段会员前台未上线，预留供后续 nook-portal 使用。
 */
public final class StpMemberUtil {

    public static final String LOGIN_TYPE = "member";

    private static final StpLogic STP_LOGIC = new StpLogic(LOGIN_TYPE);

    private StpMemberUtil() {
    }

    public static StpLogic stpLogic() {
        return STP_LOGIC;
    }

    public static void login(Object userId) {
        STP_LOGIC.login(userId);
    }

    public static String getTokenValue() {
        return STP_LOGIC.getTokenValue();
    }

    public static long getTokenTimeout() {
        return STP_LOGIC.getTokenTimeout();
    }

    public static String getLoginIdAsString() {
        return STP_LOGIC.getLoginIdAsString();
    }

    public static boolean isLogin() {
        return STP_LOGIC.isLogin();
    }

    public static void checkLogin() {
        STP_LOGIC.checkLogin();
    }

    public static void logout() {
        STP_LOGIC.logout();
    }
}
