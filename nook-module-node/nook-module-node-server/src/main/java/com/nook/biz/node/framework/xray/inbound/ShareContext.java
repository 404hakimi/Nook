package com.nook.biz.node.framework.xray.inbound;

import lombok.Builder;
import lombok.Data;

/**
 * 分享渲染上下文; 调用方 (XrayShareApiImpl) 备好客户面入参, 协议实现据此 + 自己的 params 拼链接 / proxy
 *
 * @author nook
 */
@Data
@Builder
public class ShareContext {

    /** 线路机出网 IP (host 回退); vmess 绑域名时优先用域名, vless / 纯 ws 用它. */
    private String serverIp;

    /** 对外端口 (已应用默认 443). */
    private int port;

    /** 客户连接身份 uuid. */
    private String uuid;

    /** 展示名: vmess ps / vless #备注 / clash name (协议内按需 urlEncode). */
    private String label;
}
