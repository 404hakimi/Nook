package com.nook.biz.member.api.dto;

import lombok.Data;

/**
 * 订阅会员视图 (订阅 URL 解析 sub_token 用; 跨模块契约).
 *
 * @author nook
 */
@Data
public class MemberSubscriberDTO {

    /** member_user.id. */
    private String id;

    /** 邮箱 (节点备注可用). */
    private String email;
}
