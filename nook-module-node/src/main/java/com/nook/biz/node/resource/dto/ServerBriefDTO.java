package com.nook.biz.node.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Server 列表/详情 enrich 用的轻量 DTO; 不含 SSH 凭据等敏感字段.
 *
 * @author nook
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerBriefDTO {

    private String serverId;

    /** 别名 */
    private String name;

    /** 公网/可达主机 */
    private String host;
}
