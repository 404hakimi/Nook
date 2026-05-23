package com.nook.biz.node.controller.resource.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 服务器核心字段 Response VO (SSH/账面/DNS 各走自己 endpoint)
 *
 * @author nook
 */
@Data
public class ResourceServerRespVO {

    private String id;
    private String name;

    /** 装机生命周期; 取值见 {@link ResourceServerLifecycleEnum}. */
    private String lifecycleState;

    private Integer totalIpCount;

    /** 区域码 (FK → resource_region.code). */
    private String region;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
