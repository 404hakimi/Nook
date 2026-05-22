package com.nook.biz.system.api.user;

import java.util.Collection;
import java.util.Map;

/**
 * 后台用户 Api 接口
 *
 * @author nook
 */
public interface SystemUserApi {

    /**
     * 按 id 批量拉 admin 用户的展示名 (realName 优先, 退化 username).
     *
     * @param userIds admin 用户 id 集合
     * @return key=userId, value=显示名; 缺失 id 不在 map 里
     */
    Map<String, String> getUserNameMap(Collection<String> userIds);
}
