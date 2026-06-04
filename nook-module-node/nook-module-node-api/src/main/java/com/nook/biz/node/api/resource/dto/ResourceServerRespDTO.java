package com.nook.biz.node.api.resource.dto;

import lombok.Data;

/**
 * 资源服务器 Response DTO (核心字段; SSH 凭据 / 账面 / DNS 各走自己的 Api).
 *
 * @author nook
 */
@Data
public class ResourceServerRespDTO {

    /** 服务器编号. */
    private String id;

    /** 服务器别名. */
    private String name;

    /** agent 角色: frontline=线路机 / landing=落地机. */
    private String serverType;

    /** 出网真实 IP; landing 必填, frontline 选填 (出口 IP). */
    private String ipAddress;

    /** 生命周期状态: 装机中 / 待上线 / 运行中 / 已退役. */
    private String lifecycleState;

    /** 区域码. */
    private String region;

    /** 可用 IP 总数. */
    private Integer totalIpCount;

    /** 备注. */
    private String remark;

    /** Agent 鉴权 token; createServer 时一次性签发. */
    private String agentToken;
}
