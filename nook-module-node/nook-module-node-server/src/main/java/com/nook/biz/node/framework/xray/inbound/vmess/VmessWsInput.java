package com.nook.biz.node.framework.xray.inbound.vmess;

import com.nook.biz.node.framework.xray.inbound.InboundProtocolInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * vmess + WebSocket 装机入参; ws 接入路径 + (可选) 绑域名走 TLS。
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class VmessWsInput extends InboundProtocolInput {

    /** WebSocket 接入路径; 必填, 以 / 开头。 */
    @NotBlank(message = "wsPath 必填")
    @Pattern(regexp = "^/[A-Za-z0-9_\\-/]{0,127}$", message = "wsPath 必须以 / 开头, 仅字母数字_-/")
    @Size(max = 128)
    private String wsPath;

    /** 绑定根域 system_domain.id; 非空走 TLS (CF A 记录 + acme + xray TLS), 空走纯 ws。 */
    @Size(max = 32)
    private String domainId;

    /** 二级域名标签 (如 frontline-jp-1); 绑域名 (domainId 非空) 时必填, 由 VmessWsProtocol 强制。 */
    @Pattern(regexp = "^$|^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*$",
            message = "subdomain 只能含字母数字与连字符 (可多级, 点分隔)")
    @Size(max = 128)
    private String subdomain;
}
