package com.nook.biz.member.api;

import com.nook.biz.member.api.dto.MemberSubscriberDTO;

import java.util.Collection;
import java.util.Map;

/**
 * 会员 Api 接口 (跨模块契约).
 *
 * @author nook
 */
public interface MemberUserApi {

    /**
     * 按订阅 token 查询启用会员(token 无效或会员禁用返 null)
     *
     * @param subToken 订阅 token
     * @return MemberSubscriberDTO
     */
    MemberSubscriberDTO getActiveBySubToken(String subToken);

    /**
     * 根据会员ID批量查询会员邮箱(key=会员ID, value=会员邮箱; 找不到的 id 不进 map)
     *
     * @param ids 会员ID集合
     * @return Map<String, String>
     */
    Map<String, String> getEmailMap(Collection<String> ids);
}
