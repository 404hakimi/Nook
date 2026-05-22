package com.nook.biz.node.controller.operation.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - Op 配置 Resp VO
 *
 * @author nook
 */
@Data
public class OpConfigRespVO {

    private String id;
    private String opType;
    private String name;
    private Integer execTimeoutSeconds;
    private Integer waitTimeoutSeconds;
    private Integer maxRetry;
    private Boolean enabled;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
