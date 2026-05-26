package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * 资源服务器 Response DTO (核心字段; SSH 凭据 / 账面 / DNS 各走自己的 Api).
 *
 * @author nook
 */
@Data
public class ResourceServerRespDTO {

    private String id;

    private String name;

    /** agent 角色: frontline=线路机 / landing=落地机. */
    private String serverType;

    /** 出网真实 IP; landing 必填, frontline 选填 (出口 IP). */
    private String ipAddress;

    /** Server lifecycle 状态 (INSTALLING / READY / LIVE / RETIRED). */
    private String lifecycleState;

    /** 区域码 (FK → system_region.code). */
    private String region;

    private Integer totalIpCount;

    private String remark;

    /** Agent 鉴权 token; createServer 时一次性签发. */
    private String agentToken;
}
