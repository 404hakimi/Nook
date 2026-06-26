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
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - Xray 共享 inbound 配置 Request VO
 *
 * @author nook
 */
@Data
public class XrayInboundConfigVO {

    /** 协议判别键; vmess (走 ws) / vless (走 reality); 取值合法性由 InboundProtocolFactory 解析时校验, 不在此枚举列举 */
    @NotBlank(message = "protocol 必填")
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
