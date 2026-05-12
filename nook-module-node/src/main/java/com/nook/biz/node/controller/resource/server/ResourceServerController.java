package com.nook.biz.node.controller.resource.server;

import com.nook.biz.node.controller.resource.server.vo.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.server.vo.ResourceServerRespVO;
import com.nook.biz.node.controller.resource.server.vo.ResourceServerSaveReqVO;
import com.nook.biz.node.convert.resource.ResourceServerConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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

/**
 * 管理后台 - 资源服务器
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/servers")
@Validated
public class ResourceServerController {

    @Resource
    private ResourceServerService resourceServerService;

    @PostMapping
    public Result<ResourceServerRespVO> createServer(@Valid @RequestBody ResourceServerSaveReqVO createReqVO) {
        String id = resourceServerService.createServer(createReqVO);
        ResourceServerDO server = resourceServerService.getServer(id);
        return Result.ok(ResourceServerConvert.INSTANCE.convert(server));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateServer(@PathVariable("id") String id,
                                        @Valid @RequestBody ResourceServerSaveReqVO updateReqVO) {
        resourceServerService.updateServer(id, updateReqVO);
        return Result.ok(true);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteServer(@PathVariable("id") String id) {
        resourceServerService.deleteServer(id);
        return Result.ok(true);
    }

    @GetMapping("/{id}")
    public Result<ResourceServerRespVO> getServer(@PathVariable("id") String id) {
        ResourceServerDO server = resourceServerService.getServer(id);
        return Result.ok(ResourceServerConvert.INSTANCE.convert(server));
    }

    @GetMapping
    public Result<PageResult<ResourceServerRespVO>> getServerPage(@ModelAttribute ResourceServerPageReqVO pageReqVO) {
        PageResult<ResourceServerDO> pageResult = resourceServerService.getServerPage(pageReqVO);
        return Result.ok(ResourceServerConvert.INSTANCE.convertPage(pageResult));
    }
}
