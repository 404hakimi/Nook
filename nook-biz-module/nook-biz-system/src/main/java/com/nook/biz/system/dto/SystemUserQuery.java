package com.nook.biz.system.dto;

import lombok.Data;

/** 后台用户列表查询参数。 */
@Data
public class SystemUserQuery {

    private Integer page = 1;

    private Integer size = 20;

    /** 模糊匹配 username / realName / email */
    private String keyword;

    /** 1=正常 2=禁用；不传则不过滤 */
    private Integer status;

    /** super_admin / operator / devops；不传则不过滤 */
    private String role;
}
