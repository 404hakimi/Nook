package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpTypeRespVO;
import com.nook.biz.node.convert.resource.ResourceIpPoolConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpTypeDO;
import com.nook.biz.node.service.resource.ResourceIpTypeService;
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
@RequestMapping("/admin/resource/ip-type")
@Validated
@RequiredArgsConstructor
public class ResourceIpTypeController {

    private final ResourceIpTypeService resourceIpTypeService;

    /**
     * 获得 IP 类型列表
     *
     * @return IP 类型列表
     */
    @GetMapping("/list")
    public Result<List<ResourceIpTypeRespVO>> getIpTypeList() {
        List<ResourceIpTypeDO> list = resourceIpTypeService.getIpTypeList();
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertTypeList(list));
    }
}
