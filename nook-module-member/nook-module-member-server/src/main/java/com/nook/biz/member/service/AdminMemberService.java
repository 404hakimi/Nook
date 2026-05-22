package com.nook.biz.member.service;

import com.nook.biz.member.controller.admin.vo.AdminMemberPageReqVO;
import com.nook.biz.member.entity.MemberUser;
import com.nook.common.web.response.PageResult;

/** Admin 后台对会员的管理操作: 列表 / 详情 / 禁用 / 启用 / 备注. */
public interface AdminMemberService {

    /** 列表分页查询. */
    PageResult<MemberUser> page(AdminMemberPageReqVO reqVO);

    /** 详情. */
    MemberUser findById(String id);

    /** 禁用会员; 同时踢出 sa-token 会话, 已签发的 token 立即失效. */
    void disable(String id);

    /** 启用会员. */
    void enable(String id);

    /** 修改备注. */
    void updateRemark(String id, String remark);
}
