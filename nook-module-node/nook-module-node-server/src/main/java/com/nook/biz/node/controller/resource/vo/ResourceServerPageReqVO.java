package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 管理后台 - 服务器分页查询 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceServerPageReqVO extends PageParam {

    /** 名称模糊匹 (name only; domain 在 dns 子表, host 在 credential 子表, 后端分别 join). */
    private String name;

    /** IP / host 模糊匹 (走 credential 子表). */
    private String host;

    /** 装机生命周期过滤; 取值见 {@link ResourceServerLifecycleEnum}. */
    private String lifecycleState;

    /** 区域过滤 (多选, 命中任一即可; 空=不过滤). */
    private List<String> regionCodes;

    /** server_type 过滤 (frontline / landing); null = 不过滤. */
    private String serverType;
}
