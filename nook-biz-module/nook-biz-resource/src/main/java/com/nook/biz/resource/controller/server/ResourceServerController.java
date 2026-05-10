package com.nook.biz.resource.controller.server;

import com.nook.biz.resource.controller.server.vo.ResourceServerPageReqVO;
import com.nook.biz.resource.controller.server.vo.ResourceServerRespVO;
import com.nook.biz.resource.controller.server.vo.ResourceServerSaveReqVO;
import com.nook.biz.resource.convert.ResourceServerConvert;
import com.nook.biz.resource.service.ResourceServerService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务器管理接口; controller 仅做参数绑定 + 调 service, 校验由 service 注入的 Validator 在内部完成.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/servers")
@RequiredArgsConstructor
public class ResourceServerController {

    private final ResourceServerService resourceServerService;

    @GetMapping
    public Result<PageResult<ResourceServerRespVO>> page(@ModelAttribute ResourceServerPageReqVO reqVO) {
        return Result.ok(ResourceServerConvert.INSTANCE.convertPage(resourceServerService.page(reqVO)));
    }

    @GetMapping("/{id}")
    public Result<ResourceServerRespVO> detail(@PathVariable String id) {
        return Result.ok(ResourceServerConvert.INSTANCE.convert(resourceServerService.findById(id)));
    }

    @PostMapping
    public Result<ResourceServerRespVO> create(@RequestBody @Valid ResourceServerSaveReqVO reqVO) {
        return Result.ok(ResourceServerConvert.INSTANCE.convert(resourceServerService.create(reqVO)));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id,
                               @RequestBody @Valid ResourceServerSaveReqVO reqVO) {
        resourceServerService.update(id, reqVO);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        resourceServerService.delete(id);
        return Result.ok();
    }
}
