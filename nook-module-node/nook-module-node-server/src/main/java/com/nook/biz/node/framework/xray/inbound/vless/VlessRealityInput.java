package com.nook.biz.node.framework.xray.inbound.vless;

import com.nook.biz.node.framework.xray.inbound.InboundProtocolInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * vless + REALITY 装机入参; 偷取目标真站主机名。
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class VlessRealityInput extends InboundProtocolInput {

    /** REALITY 偷取目标主机名 (如 www.bing.com; 预设或自定义); 必填, 须合法 FQDN。 */
    @NotBlank(message = "realityDest 必填")
    @Pattern(regexp = "^(?=.{1,253}$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))+$",
            message = "realityDest 须是合法主机名 (如 www.bing.com)")
    @Size(max = 253)
    private String realityDest;
}
