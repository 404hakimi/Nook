package com.nook.biz.resource.controller.server.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceServerPageReqVO extends PageParam {

    /** 关键词，模糊匹配 name / host */
    private String keyword;

    /** 1=运行 2=维护 3=下线 */
    private Integer status;

    /** 区域: us-west / us-east / jp / hk */
    private String region;
}
