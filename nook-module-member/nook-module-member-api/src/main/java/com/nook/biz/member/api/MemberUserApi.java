package com.nook.biz.member.api;

import com.nook.biz.member.api.dto.MemberSubscriberDTO;

/**
 * 会员 Api 接口 (跨模块契约).
 *
 * @author nook
 */
public interface MemberUserApi {

    /**
     * 按订阅 token 查启用会员; token 无效或会员禁用返 null.
     *
     * @param subToken member_user.sub_token
     * @return 会员视图; 不存在 / 禁用返 null
     */
    MemberSubscriberDTO getActiveBySubToken(String subToken);
}
