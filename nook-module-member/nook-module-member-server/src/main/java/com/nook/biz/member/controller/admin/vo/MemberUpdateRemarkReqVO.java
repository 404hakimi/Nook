package com.nook.biz.member.controller.admin.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 会员备注更新 Request VO
 *
 * @author nook
 */
@Data
public class MemberUpdateRemarkReqVO {

    /** 管理员备注. */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
