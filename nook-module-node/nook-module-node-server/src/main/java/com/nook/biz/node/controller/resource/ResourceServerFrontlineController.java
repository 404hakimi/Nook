package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerFrontlineRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerFrontlineUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.ServerFrontlineListItemRespVO;
import com.nook.biz.node.convert.resource.ResourceServerFrontlineConvert;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private ResourceServerService resourceServerService;
    @Resource
    private ResourceServerFrontlineService resourceServerFrontlineService;

    /**
     * 获得线路机分页 (连表出运行时聚合视图: 在线态 / 版本 / 配额 / 流量 / throttle)
     *
     * @param reqVO 分页条件
     * @return 线路机列表项分页
     */
    @GetMapping("/page-frontline")
    public Result<PageResult<ServerFrontlineListItemRespVO>> getPage(@ModelAttribute ResourceServerPageReqVO reqVO) {
        return Result.ok(resourceServerFrontlineService.getFrontlinePage(reqVO));
    }

    /**
     * 获得线路机扩展
     *
     * @param id 线路机编号
     * @return 线路机扩展
     */
    @GetMapping("/get-frontline")
    public Result<ResourceServerFrontlineRespVO> getFrontline(@RequestParam("id") String id) {
        resourceServerService.requireServer(id);
        return Result.ok(ResourceServerFrontlineConvert.INSTANCE.convert(resourceServerFrontlineService.get(id)));
    }

    /**
     * 更新线路机扩展
     *
     * @param id    线路机编号
     * @param reqVO 更新入参
     * @return 是否成功
     */
    @PutMapping("/update-frontline")
    public Result<Boolean> updateFrontline(@RequestParam("id") String id,
                                           @Valid @RequestBody ResourceServerFrontlineUpdateReqVO reqVO) {
        resourceServerFrontlineService.update(id, reqVO);
        return Result.ok(true);
    }
}
