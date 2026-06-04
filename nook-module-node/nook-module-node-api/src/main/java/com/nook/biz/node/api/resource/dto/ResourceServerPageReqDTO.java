package com.nook.biz.node.api.resource.dto;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资源服务器分页查询 跨模块 DTO (agent-server 拼 admin 列表用).
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceServerPageReqDTO extends PageParam {

    /** 名称模糊匹. */
    private String name;

    /** IP / 主机模糊匹配. */
    private String host;

    /** 装机生命周期: 装机中 / 待上线 / 运行中 / 已退役. */
    private String lifecycleState;

    /** 区域码. */
    private String region;
}
