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

    /** IP / host 模糊匹 (后端走 credential 子表过滤再 join 主表). */
    private String host;

    /** 装机生命周期 INSTALLING / READY / LIVE / RETIRED. */
    private String lifecycleState;

    /** 区域码. */
    private String region;
}
