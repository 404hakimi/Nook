package com.nook.biz.node.controller.xray.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - Xray 客户端分页 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class XrayClientPageReqVO extends PageParam {

    /** 关键词，模糊匹配 client_email */
    private String keyword;

    /** 服务器编号. */
    private String serverId;
    /** 会员用户编号. */
    private String memberUserId;
    /** IP 编号. */
    private String ipId;

    /** 1=运行 2=已停 3=待同步 4=远端已不存在 */
    private Integer status;
}
