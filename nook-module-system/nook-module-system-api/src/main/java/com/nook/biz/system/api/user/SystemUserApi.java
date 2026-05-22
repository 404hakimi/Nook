package com.nook.biz.system.api.user;

import java.util.Collection;
import java.util.Map;

/** System 模块对外契约: admin 用户跨模块查询 (op_log 补 operator 名等). */
public interface SystemUserApi {

    /**
     * 按 id 批量拉 admin 用户的展示名 (realName 优先, 退化 username).
     *
     * @param userIds admin 用户 id 集合
     * @return key=userId, value=显示名; 缺失 id 不在 map 里
     */
    Map<String, String> getUserNameMap(Collection<String> userIds);
}
