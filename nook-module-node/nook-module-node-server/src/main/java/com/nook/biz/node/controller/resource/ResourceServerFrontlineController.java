package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.frontline.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 线路机 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server-frontline")
@Validated
public class ResourceServerFrontlineController {

    @Resource
    private ResourceServerFrontlineService resourceServerFrontlineService;

    /**
     * 获得线路机分页 (连表出运行时聚合视图)
     *
     * @param reqVO 分页条件
     * @return 线路机列表项分页
     */
    @GetMapping("/page-frontline")
    public Result<PageResult<ServerFrontlineListItemRespVO>> getPage(@Valid ResourceServerPageReqVO reqVO) {
        return Result.ok(resourceServerFrontlineService.getFrontlinePage(reqVO));
    }

    /**
     * 切换线路机生命周期 (上线前置: 已绑域名)
     *
     * @param id    服务器ID
     * @param state 目标生命周期
     */
    @PostMapping("/transition-lifecycle")
    public Result<Void> transitionLifecycle(@RequestParam("id") String id,
                                            @RequestParam("state") String state) {
        resourceServerFrontlineService.transitionLifecycle(id, state);
        return Result.ok();
    }
}
