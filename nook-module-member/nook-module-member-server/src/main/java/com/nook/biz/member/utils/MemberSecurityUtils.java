package com.nook.biz.member.utils;

import cn.hutool.core.util.IdUtil;

/**
 * 会员安全工具
 *
 * @author nook
 */
public final class MemberSecurityUtils {

    private MemberSecurityUtils() {
    }

    /**
     * 生成订阅 token (32 位 hex, 以 UUID 保证全局唯一)
     *
     * @return 订阅 token
     */
    public static String generateSubToken() {
        return IdUtil.simpleUUID();
    }
}
