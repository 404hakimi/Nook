package com.nook.biz.member.controller.admin.vo;

import com.nook.common.web.request.PageParam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class AdminMemberPageReqVO extends PageParam {

    /** 邮箱模糊匹配. */
    @Size(max = 128, message = "搜索关键字长度不能超过 128")
    private String keyword;

    /** 状态过滤; null=不过滤. 1=正常 2=禁用 */
    @Min(value = 1) @Max(value = 2)
    private Integer status;
}
