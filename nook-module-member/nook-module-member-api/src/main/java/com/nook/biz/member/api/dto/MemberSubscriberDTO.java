package com.nook.biz.member.api.dto;

import lombok.Data;

/**
 * 订阅会员视图 DTO
 *
 * @author nook
 */
@Data
public class MemberSubscriberDTO {

    /** 会员ID. */
    private String id;

    /** 邮箱. */
    private String email;
}
