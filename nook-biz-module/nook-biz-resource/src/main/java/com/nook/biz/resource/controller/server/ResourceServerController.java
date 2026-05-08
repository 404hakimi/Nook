package com.nook.biz.resource.controller.server;

import com.nook.biz.resource.controller.server.vo.ResourceServerPageReqVO;
import com.nook.biz.resource.controller.server.vo.ResourceServerRespVO;
import com.nook.biz.resource.controller.server.vo.ResourceServerSaveReqVO;
import com.nook.biz.resource.convert.ResourceServerConvert;
import com.nook.biz.resource.service.ResourceServerService;
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
@RequestMapping("/admin/resource/servers")
@RequiredArgsConstructor
@Validated
public class ResourceServerController {

    private final ResourceServerService resourceServerService;

    @GetMapping
    public Result<PageResult<ResourceServerRespVO>> page(@ModelAttribute ResourceServerPageReqVO reqVO) {
        return Result.ok(ResourceServerConvert.INSTANCE.convertPage(resourceServerService.page(reqVO)));
    }

    @GetMapping("/{id}")
    public Result<ResourceServerRespVO> detail(@PathVariable @NotBlank String id) {
        return Result.ok(ResourceServerConvert.INSTANCE.convert(resourceServerService.findById(id)));
    }

    @PostMapping
    public Result<ResourceServerRespVO> create(@RequestBody @Validated(Create.class) ResourceServerSaveReqVO reqVO) {
        return Result.ok(ResourceServerConvert.INSTANCE.convert(resourceServerService.create(reqVO)));
    }

    @PutMapping("/{id}")
    public Result<ResourceServerRespVO> update(@PathVariable @NotBlank String id,
                                                @RequestBody @Validated(Update.class) ResourceServerSaveReqVO reqVO) {
        return Result.ok(ResourceServerConvert.INSTANCE.convert(resourceServerService.update(id, reqVO)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @NotBlank String id) {
        resourceServerService.delete(id);
        return Result.ok();
    }
}
