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

    /** 服务器编号. */
    private String id;
    /** 服务器别名. */
    private String name;

    /** 装机生命周期; 取值见 {@link ResourceServerLifecycleEnum}. */
    private String lifecycleState;

    /** IP 总数. */
    private Integer totalIpCount;

    /** 区域码. */
    private String region;

    /** 备注. */
    private String remark;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
