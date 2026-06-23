package com.nook.biz.node.framework.xray.inbound;

import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import lombok.Builder;
import lombok.Data;

/**
 * 协议开通(provision)结果: 协议形态 + 语义参数 + 渲染好的 inbound JSON + 域名/CF token (vmess-tls 路径有值, 其余为空)
 *
 * @author nook
 */
@Data
@Builder
public class InboundProvisionResult {

    /** 协议形态 {@link XrayInboundProtocolEnum}. */
    private XrayInboundProtocolEnum protocol;

    /** 协议/传输/安全语义参数; 落库 params 列. */
    private InboundParams params;

    /** 渲染好的 in_shared inbound JSON; 装机下发 / 重建用. */
    private String inboundJson;

    /** 对外完整域名 FQDN; vmess-tls 有值, 其余为空. */
    private String fullDomain;

    /** Cloudflare API token; vmess-tls 加 A 记录用, 其余为空. */
    private String cfApiToken;

    /** 绑定根域 system_domain.id; vmess-tls 有值, 其余空. persistDeployment 据此回填 xray_install, 不再从输入 VO 取协议特化字段. */
    private String domainId;

    /** 二级域名标签; vmess-tls 有值, 其余空. */
    private String subdomain;
}
