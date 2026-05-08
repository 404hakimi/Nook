package com.nook.biz.xray.controller.client.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改 inbound 行的元数据(本地字段)。
 * <p>能改的全部是"DB 元数据"——不会触达远端 backend：
 *   listenIp / listenPort / transport：仅本地展示与排查用，不影响远端 client；
 *   status：手工状态覆写(运营标"已停"或修正 reconciler 误标的"待同步")。
 * <p>**不允许在此改的字段**：serverId / ipId / memberUserId / protocol / clientUuid / clientEmail /
 *  externalInboundRef——这些是开通时确定的契约，要换需 revoke + 重新 provision。
 */
@Data
public class XrayClientUpdateReqVO {

    @Size(max = 45, message = "listenIp 长度不能超过 45")
    private String listenIp;

    @Min(value = 1, message = "listenPort 最小 1")
    @Max(value = 65535, message = "listenPort 最大 65535")
    private Integer listenPort;

    @Size(max = 32, message = "transport 长度不能超过 32")
    private String transport;

    /** 1=运行 2=已停 3=待同步 4=远端缺失 */
    @Min(value = 1, message = "status 取值 1-4")
    @Max(value = 4, message = "status 取值 1-4")
    private Integer status;
}
