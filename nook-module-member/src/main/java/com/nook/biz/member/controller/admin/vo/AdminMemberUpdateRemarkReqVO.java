package com.nook.biz.member.controller.admin.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Admin 编辑会员备注. */
@Data
public class AdminMemberUpdateRemarkReqVO {

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
