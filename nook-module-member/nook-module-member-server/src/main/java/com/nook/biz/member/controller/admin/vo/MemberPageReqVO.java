package com.nook.biz.member.controller.admin.vo;

import com.nook.biz.member.api.enums.MemberUserStatusEnum;
import com.nook.common.web.request.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 会员分页 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberPageReqVO extends PageParam {

    /** 邮箱模糊匹配. */
    @Size(max = 128, message = "搜索关键字长度不能超过 128")
    private String keyword;

    /** 状态过滤 {@link MemberUserStatusEnum}; null=不过滤. */
    private Integer status;
}
