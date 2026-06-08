package com.nook.biz.node.controller.resource.vo.frontline;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 线路机扩展更新 Request VO
 *
 * @author nook
 */
@Data
public class ResourceServerFrontlineUpdateReqVO {

    @Size(max = 128)
    private String domain;

    @Size(max = 64)
    private String cfZoneId;

    @Size(max = 64)
    private String cfRecordId;
}
