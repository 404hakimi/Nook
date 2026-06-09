package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.frontline.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 线路机 Controller (机器分页 / 生命周期; 域名见域名管理页 + 装机绑定)
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server-frontline")
@Validated
public class ResourceServerFrontlineController {

    @Resource
    private ResourceServerFrontlineService resourceServerFrontlineService;

    /** 线路机分页 (连表出运行时聚合视图: 在线态 / 版本 / 配额 / 流量 / throttle). */
    @GetMapping("/page-frontline")
    public Result<PageResult<ServerFrontlineListItemRespVO>> getPage(@ModelAttribute ResourceServerPageReqVO reqVO) {
        return Result.ok(resourceServerFrontlineService.getFrontlinePage(reqVO));
    }

    /** 切换线路机生命周期 (上线 / 退役; 上线前置: xray_install 已绑域名). */
    @PostMapping("/transition-lifecycle")
    public Result<Boolean> transitionLifecycle(@RequestParam("id") String id,
                                               @RequestParam("state") String state) {
        resourceServerFrontlineService.transitionLifecycle(id, state);
        return Result.ok(true);
    }
}
