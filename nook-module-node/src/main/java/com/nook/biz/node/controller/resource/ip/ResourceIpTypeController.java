package com.nook.biz.node.controller.resource.ip;

import com.nook.biz.node.controller.resource.ip.vo.ResourceIpTypeRespVO;
import com.nook.biz.node.convert.resource.ResourceIpPoolConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpTypeDO;
import com.nook.biz.node.service.resource.ResourceIpTypeService;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - IP 类型
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/ip-types")
@Validated
public class ResourceIpTypeController {

    @Resource
    private ResourceIpTypeService resourceIpTypeService;

    @GetMapping
    public Result<List<ResourceIpTypeRespVO>> getIpTypeList() {
        List<ResourceIpTypeDO> list = resourceIpTypeService.getIpTypeList();
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertTypeList(list));
    }
}
