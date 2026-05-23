package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 服务器 DNS 绑定 Update Request VO (Create 流程嵌套在 ResourceServerCreateReqVO.dns 内复用)
 *
 * @author nook
 */
@Data
public class ResourceServerDnsUpdateReqVO {

    @Size(max = 128)
    private String domain;

    @Size(max = 64)
    private String cfZoneId;

    @Size(max = 64)
    private String cfRecordId;
}
