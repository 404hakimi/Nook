package com.nook.biz.node.framework.xray.inbound.protocol;

import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;

import java.util.function.Consumer;

/**
 * 协议装机上下文; 协议特定前置 (域名解析 / CF A 记录 / 密钥生成) 所需的运行期入参
 *
 * @author nook
 */
public record InboundProvisionContext(String serverId, XrayInstallReqVO reqVO, String serverIp,
                                      Consumer<String> lineSink) {
}
