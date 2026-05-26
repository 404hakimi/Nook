package com.nook.biz.system.controller.iptype;

import com.nook.biz.system.controller.iptype.vo.SystemIpTypeRespVO;
import com.nook.biz.system.dal.dataobject.iptype.SystemIpTypeDO;
import com.nook.biz.system.service.iptype.SystemIpTypeService;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.Result;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SystemIpTypeController {

    private final SystemIpTypeService systemIpTypeService;

    /**
     * 获得 IP 类型列表
     *
     * @return IP 类型列表
     */
    @GetMapping("/list")
    public Result<List<SystemIpTypeRespVO>> getIpTypeList() {
        List<SystemIpTypeDO> list = systemIpTypeService.getIpTypeList();
        return Result.ok(BeanUtils.toBean(list, SystemIpTypeRespVO.class));
    }
}
