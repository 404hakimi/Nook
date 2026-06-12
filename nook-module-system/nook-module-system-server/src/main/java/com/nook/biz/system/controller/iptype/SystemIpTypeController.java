package com.nook.biz.system.controller.iptype;

import com.nook.biz.system.controller.iptype.vo.SystemIpTypeRespVO;
import com.nook.biz.system.convert.iptype.SystemIpTypeConvert;
import com.nook.biz.system.entity.SystemIpTypeDO;
import com.nook.biz.system.service.SystemIpTypeService;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - IP 类型 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/system/ip-type")
@Validated
public class SystemIpTypeController {

    @Resource
    private SystemIpTypeService systemIpTypeService;

    /**
     * 获得 IP 类型列表
     *
     * @return IP 类型列表
     */
    @GetMapping("/list-ip-type")
    public Result<List<SystemIpTypeRespVO>> getIpTypeList() {
        // 查询列表
        List<SystemIpTypeDO> types = systemIpTypeService.getIpTypeList();
        // 转换返回
        return Result.ok(SystemIpTypeConvert.INSTANCE.convertList(types));
    }
}
