package com.nook.biz.xray.controller.client.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class XrayClientPageReqVO extends PageParam {

    /** 关键词，模糊匹配 client_email */
    private String keyword;

    private String serverId;
    private String memberUserId;
    private String ipId;

    /** 1=运行 2=已停 3=待同步 4=远端已不存在 */
    private Integer status;
}
