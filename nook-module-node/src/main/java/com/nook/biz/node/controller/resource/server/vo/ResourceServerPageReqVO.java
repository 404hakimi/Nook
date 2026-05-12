package com.nook.biz.node.controller.resource.server.vo;

import com.nook.biz.node.enums.ResourceServerStatusEnum;
import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务器分页查询 Request VO.
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceServerPageReqVO extends PageParam {

    /** 关键词; 模糊匹配 name / host */
    private String keyword;

    /** 状态; 取值见 {@link ResourceServerStatusEnum} */
    private Integer status;

    /** 区域 (us-west / us-east / jp / hk 等) */
    private String region;
}
