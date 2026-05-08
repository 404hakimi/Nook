package com.nook.biz.resource.controller.ip;

import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolRespVO;
import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.resource.controller.ip.vo.Socks5TestRespVO;
import com.nook.biz.resource.convert.ResourceIpPoolConvert;
import com.nook.biz.resource.service.ResourceIpPoolService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import com.nook.common.web.validation.Create;
import com.nook.common.web.validation.Update;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/resource/ip-pool")
@RequiredArgsConstructor
@Validated
public class ResourceIpPoolController {

    private final ResourceIpPoolService resourceIpPoolService;

    @GetMapping
    public Result<PageResult<ResourceIpPoolRespVO>> page(@ModelAttribute ResourceIpPoolPageReqVO reqVO) {
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertPage(resourceIpPoolService.page(reqVO)));
    }

    @GetMapping("/{id}")
    public Result<ResourceIpPoolRespVO> detail(@PathVariable @NotBlank String id) {
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convert(resourceIpPoolService.findById(id)));
    }

    @PostMapping
    public Result<ResourceIpPoolRespVO> create(@RequestBody @Validated(Create.class) ResourceIpPoolSaveReqVO reqVO) {
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convert(resourceIpPoolService.create(reqVO)));
    }

    @PutMapping("/{id}")
    public Result<ResourceIpPoolRespVO> update(@PathVariable @NotBlank String id,
                                                @RequestBody @Validated(Update.class) ResourceIpPoolSaveReqVO reqVO) {
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convert(resourceIpPoolService.update(id, reqVO)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @NotBlank String id) {
        resourceIpPoolService.delete(id);
        return Result.ok();
    }

    /** 退订: 仅状态机切换 (occupied → cooling); 真正回到 available 由调度器 sweep 完成。 */
    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable @NotBlank String id) {
        resourceIpPoolService.releaseToCooling(id);
        return Result.ok();
    }

    /** SOCKS5 连通性测试: nook 后端通过 IP 的 SOCKS5 凭据拨号 echo-IP 端点; 失败也返回 success=false 不抛错。 */
    @PostMapping("/{id}/test")
    public Result<Socks5TestRespVO> testSocks5(@PathVariable @NotBlank String id) {
        return Result.ok(resourceIpPoolService.testSocks5(id));
    }
}
