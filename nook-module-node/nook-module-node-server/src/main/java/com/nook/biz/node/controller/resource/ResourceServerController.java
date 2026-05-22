package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerCapacityRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerSaveReqVO;
import com.nook.biz.node.convert.resource.ResourceServerConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 资源服务器 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server")
@Validated
@RequiredArgsConstructor
public class ResourceServerController {

    private final ResourceServerService resourceServerService;
    private final ResourceServerValidator serverValidator;
    private final ResourceServerCapacityMapper resourceServerCapacityMapper;

    @PostMapping("/create")
    public Result<ResourceServerRespVO> createServer(@Valid @RequestBody ResourceServerSaveReqVO createReqVO) {
        String id = resourceServerService.createServer(createReqVO);
        ResourceServerDO server = serverValidator.validateExists(id);
        return Result.ok(ResourceServerConvert.INSTANCE.convert(server));
    }

    @PutMapping("/update")
    public Result<Boolean> updateServer(@RequestParam("id") String id,
                                        @Valid @RequestBody ResourceServerSaveReqVO updateReqVO) {
        resourceServerService.updateServer(id, updateReqVO);
        return Result.ok(true);
    }

    @DeleteMapping("/delete")
    public Result<Boolean> deleteServer(@RequestParam("id") String id) {
        resourceServerService.deleteServer(id);
        return Result.ok(true);
    }

    @GetMapping("/get")
    public Result<ResourceServerRespVO> getServer(@RequestParam("id") String id) {
        ResourceServerDO server = serverValidator.validateExists(id);
        return Result.ok(ResourceServerConvert.INSTANCE.convert(server));
    }

    @GetMapping("/page")
    public Result<PageResult<ResourceServerRespVO>> getServerPage(@ModelAttribute ResourceServerPageReqVO pageReqVO) {
        PageResult<ResourceServerDO> pageResult = resourceServerService.getServerPage(pageReqVO);
        return Result.ok(ResourceServerConvert.INSTANCE.convertPage(pageResult));
    }

    /** 切换 lifecycle_state; admin 上线 / 退役流转用. */
    @PostMapping("/lifecycle")
    public Result<Boolean> transitionLifecycle(@RequestParam("id") String id,
                                               @RequestParam("state") String state) {
        resourceServerService.transitionLifecycle(id, state);
        return Result.ok(true);
    }

    /** 取 server 流量配额 + 已用 (监控面板用); 未上报过 NIC 时返 null. */
    @GetMapping("/capacity")
    public Result<ResourceServerCapacityRespVO> getCapacity(@RequestParam("id") String id) {
        serverValidator.validateExists(id);
        ResourceServerCapacityDO row = resourceServerCapacityMapper.selectById(id);
        return Result.ok(row == null ? null : BeanUtils.toBean(row, ResourceServerCapacityRespVO.class));
    }

}
