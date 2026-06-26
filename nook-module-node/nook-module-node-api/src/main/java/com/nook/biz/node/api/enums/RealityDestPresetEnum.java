package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * REALITY 偷取目标真站候选 (装机时后台可选, 客户端 SNI 伪装成它)
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum RealityDestPresetEnum {

    MICROSOFT("www.microsoft.com", "微软"),
    APPLE("www.apple.com", "苹果"),
    CLOUDFLARE("www.cloudflare.com", "Cloudflare"),
    AMAZON("www.amazon.com", "亚马逊"),
    BING("www.bing.com", "必应"),
    ;

    /** 伪装 SNI = dest 主机名. */
    private final String serverName;

    /** 展示标签. */
    private final String label;

    /** dest 固定 443 端口 (TLS 站). */
    public String getDest() {
        return serverName + ":443";
    }
}
