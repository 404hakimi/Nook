package com.nook.biz.node.api.resource.dto;

import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import lombok.Data;

/**
 * 资源服务器 Response DTO
 *
 * @author nook
 */
@Data
public class ResourceServerRespDTO {

    /** 服务器ID. */
    private String id;

    /** 服务器别名. */
    private String name;

    /** 服务器类型 {@link ResourceServerTypeEnum} */
    private String serverType;

    /** 出网真实 IP; 落地机必填, 线路机选填. */
    private String ipAddress;

    /** 生命周期状态 {@link ResourceServerLifecycleEnum} */
    private String lifecycleState;

    /** 区域码. */
    private String region;

    /** 备注. */
    private String remark;

    /** Agent 鉴权 token. */
    private String agentToken;
}
