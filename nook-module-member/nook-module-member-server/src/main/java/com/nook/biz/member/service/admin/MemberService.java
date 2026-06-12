package com.nook.biz.member.service.admin;

import com.nook.biz.member.controller.admin.vo.MemberPageReqVO;
import com.nook.biz.member.entity.MemberUser;
import com.nook.common.web.response.PageResult;

/**
 * 管理后台 - 会员管理 Service 接口
 *
 * @author nook
 */
public interface MemberService {

    /**
     * 获得会员分页列表
     *
     * @param reqVO 分页条件
     * @return 分页列表
     */
    PageResult<MemberUser> page(MemberPageReqVO reqVO);

    /**
     * 获得会员详情
     *
     * @param id 会员ID
     * @return 会员信息
     */
    MemberUser findById(String id);

    /**
     * 获得会员订阅分享 URL
     *
     * @param id 会员ID
     * @return 订阅分享 URL
     */
    String getSubUrl(String id);

    /**
     * 禁用会员并踢出已有会话
     *
     * @param id 会员ID
     */
    void disable(String id);

    /**
     * 启用会员
     *
     * @param id 会员ID
     */
    void enable(String id);

    /**
     * 修改备注
     *
     * @param id     会员ID
     * @param remark 备注
     */
    void updateRemark(String id, String remark);

    /**
     * 重置会员密码并踢出已有会话
     *
     * @param id       会员ID
     * @param password 新密码
     */
    void resetPassword(String id, String password);
}
