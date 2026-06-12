package com.nook.biz.member.service.portal;

import com.nook.biz.member.controller.portal.vo.PortalChangePasswordReqVO;
import com.nook.biz.member.entity.MemberUser;

/**
 * 客户端 - 会员资料 Service 接口
 *
 * @author nook
 */
public interface PortalMemberService {

    /**
     * 获得会员信息
     *
     * @param memberId 会员ID
     * @return 会员信息
     */
    MemberUser getMemberUser(String memberId);

    /**
     * 修改登录密码
     *
     * @param memberId 会员ID
     * @param reqVO    修改密码信息
     */
    void changePassword(String memberId, PortalChangePasswordReqVO reqVO);

    /**
     * 重置订阅 token
     *
     * @param memberId 会员ID
     * @return 新订阅 token
     */
    String resetSubToken(String memberId);
}
