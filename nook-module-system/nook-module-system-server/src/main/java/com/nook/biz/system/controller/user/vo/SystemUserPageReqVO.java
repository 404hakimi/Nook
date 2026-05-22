package com.nook.biz.system.controller.user.vo;

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

    /** 模糊匹配 username / realName / email */
    private String keyword;

    /** 1=正常 2=禁用；不传则不过滤 */
    private Integer status;

    /** super_admin / operator / devops；不传则不过滤 */
    private String role;
}
