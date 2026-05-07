package com.nook.biz.system.controller.user.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 后台用户列表分页查询参数。 */
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
