package com.nook.biz.node.resource.controller.ip;

import com.nook.biz.node.resource.controller.ip.vo.ResourceIpTypeRespVO;
import com.nook.biz.node.resource.convert.ResourceIpPoolConvert;
import com.nook.biz.node.resource.service.ResourceIpTypeService;
import com.nook.common.web.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * IP 类型只读列表;  IP 池录入页 / 套餐配置页下拉用。
 * CRUD 暂不开放，初始数据由 99_seed.sql 提供。
 */
@RestController
@RequestMapping("/admin/resource/ip-types")
@RequiredArgsConstructor
public class ResourceIpTypeController {

    private final ResourceIpTypeService resourceIpTypeService;

    @GetMapping
    public Result<List<ResourceIpTypeRespVO>> list() {
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertTypeList(resourceIpTypeService.listAll()));
    }
}
