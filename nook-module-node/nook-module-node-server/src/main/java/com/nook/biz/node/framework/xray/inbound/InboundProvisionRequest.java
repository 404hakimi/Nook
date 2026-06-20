package com.nook.biz.node.framework.xray.inbound;

import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import lombok.Builder;
import lombok.Data;

import java.util.function.Consumer;

/**
 * 协议开通(provision)入参; 协议特定前置 (域名解析 / CF A 记录 / 密钥生成) 所需的运行期请求
 *
 * @author nook
 */
@Data
@Builder
public class InboundProvisionRequest {

    /** 服务器ID (线路机). */
    private String serverId;

    /** 装机入参. */
    private XrayInstallReqVO reqVO;

    /** 线路机出网 IP; CF A 记录指向它. */
    private String serverIp;

    /** 装机进度行回调; 流式回传前端. */
    private Consumer<String> lineSink;
}
