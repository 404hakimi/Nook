package com.nook.biz.system.controller.user.vo;

import com.nook.biz.system.api.enums.SystemUserRoleEnum;
import com.nook.biz.system.api.enums.SystemUserStatusEnum;
import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 后台用户分页 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemUserPageReqVO extends PageParam {

    /** 模糊匹配 username / realName / email. */
    private String keyword;

    /** 状态过滤 {@link SystemUserStatusEnum}; null=不过滤. */
    private Integer status;

    /** 角色过滤 {@link SystemUserRoleEnum}; null=不过滤. */
    private String role;
}
