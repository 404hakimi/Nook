package com.nook.biz.node.controller.xray;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.xray.vo.XrayConfigRespVO;
import com.nook.biz.node.convert.xray.XrayConfigConvert;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.service.xray.config.XrayConfigService;
import com.nook.common.web.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - Xray inbound 共享配置 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/xray/config")
@Validated
@RequiredArgsConstructor
public class XrayConfigController {

    private final XrayConfigService xrayConfigService;

    /**
     * 获得 inbound 共享配置 (server detail tab 用; 未装机时返 null)
     *
     * @param serverId 服务器编号
     * @return inbound 共享配置
     */
    @GetMapping("/get-xray-config")
    public Result<XrayConfigRespVO> getXrayConfig(@RequestParam("serverId") String serverId) {
        XrayConfigDO entity = xrayConfigService.get(serverId);
        if (ObjectUtil.isNull(entity)) return Result.ok(null);
        return Result.ok(XrayConfigConvert.INSTANCE.convert(entity));
    }
}
