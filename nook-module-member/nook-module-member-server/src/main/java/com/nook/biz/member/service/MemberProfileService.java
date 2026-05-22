package com.nook.biz.member.service;

import com.nook.biz.member.controller.portal.vo.PortalChangePasswordReqVO;
import com.nook.biz.member.controller.portal.vo.PortalMemberRespVO;

/**
 * 会员资料 Service 接口
 *
 * @author nook
 */
public interface MemberProfileService {

    /**
     * 查询当前登录会员信息.
     *
     * @param memberId 当前登录会员 id
     * @return 会员信息 VO
     */
    PortalMemberRespVO getProfile(String memberId);

    /**
     * 修改密码; 校验旧密码 + 新密码强度.
     *
     * @param memberId 当前登录会员 id
     * @param reqVO    入参
     */
    void changePassword(String memberId, PortalChangePasswordReqVO reqVO);

    /**
     * 重置 sub_token; 旧 token 立即失效 (sub 聚合 URL 失效, 下次客户端刷新订阅会拿不到 → 引导用户更新订阅 URL).
     *
     * @param memberId 当前登录会员 id
     * @return 新的 sub_token
     */
    String resetSubToken(String memberId);
}
