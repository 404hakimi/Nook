package com.nook.biz.node.controller.xray.vo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nook.biz.node.framework.xray.inbound.InboundProtocolInput;
import com.nook.biz.node.framework.xray.inbound.vless.VlessRealityInput;
import com.nook.biz.node.framework.xray.inbound.vmess.VmessWsInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Xray 共享 inbound 配置; 协议形态键 + 监听端口 + 协议特定参数 (多态 params, 按 protocol 绑定子类型)。
 *
 * <p>协议特定字段 (ws / reality / 域名) 不再平铺于本类: 各协议自有输入 DTO (见 {@link InboundProtocolInput} 子类),
 * 加协议本类零改。协议特定字段的必填性由对应 InboundProtocol 在运行期补校验。
 *
 * @author nook
 */
@Data
public class XrayInboundConfigVO {

    /** 协议; vmess (走 ws) 或 vless (走 reality); 同时是 params 的多态判别键。 */
    @NotBlank(message = "protocol 必填")
    @Pattern(regexp = "vmess|vless|trojan", message = "protocol 必须是 vmess / vless / trojan 之一")
    @Size(max = 16)
    private String protocol;

    /** 监听端口; 默认 443。 */
    @NotNull(message = "sharedInboundPort 必填")
    @Min(value = 1) @Max(value = 65535)
    private Integer sharedInboundPort;

    /**
     * 协议特定入站参数; 按兄弟字段 protocol 多态绑定到 VmessWsInput / VlessRealityInput, 各子类自带字段校验。
     * EXTERNAL_PROPERTY 必须声明在本属性 (而非基类), 否则 Jackson 拿不到外层 protocol 上下文. 加协议在 @JsonSubTypes 加一行。
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "protocol")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = VmessWsInput.class, name = "vmess"),
            @JsonSubTypes.Type(value = VlessRealityInput.class, name = "vless"),
    })
    @NotNull(message = "params 必填")
    @Valid
    private InboundProtocolInput params;
}
