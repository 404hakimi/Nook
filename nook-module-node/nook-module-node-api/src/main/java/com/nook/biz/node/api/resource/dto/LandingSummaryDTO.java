package com.nook.biz.node.api.resource.dto;

import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import lombok.Data;

/**
 * 落地机概要 DTO
 *
 * @author nook
 */
@Data
public class LandingSummaryDTO {

    /** 落地机ID. */
    private String serverId;

    /** 装机生命周期 {@link ResourceServerLifecycleEnum} */
    private String lifecycleState;

    /** IP 类型ID. */
    private String ipTypeId;

    /** 出网 IP. */
    private String ipAddress;
}
