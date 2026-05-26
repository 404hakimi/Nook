package com.nook.biz.node.controller.resource;

import com.nook.biz.agent.api.AgentRuntimeConfigApi;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerFrontlineRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerFrontlineUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerRespVO;
import com.nook.biz.node.controller.resource.vo.ServerFrontlineListItemRespVO;
import com.nook.biz.node.convert.resource.ResourceServerConvert;
import com.nook.biz.node.convert.resource.ResourceServerFrontlineConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - 线路机 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server-frontline")
@Validated
@RequiredArgsConstructor
public class ResourceServerFrontlineController {

    private final ResourceServerService resourceServerService;
    private final ResourceServerFrontlineService frontlineService;
    private final AgentRuntimeConfigApi agentRuntimeConfigApi;

    /**
     * 创建线路机
     *
     * @param reqVO 创建入参
     * @return 线路机详情
     */
    @PostMapping("/create-frontline")
    public Result<ResourceServerRespVO> createFrontline(@Valid @RequestBody ResourceServerCreateReqVO reqVO) {
        String id = resourceServerService.createServer(reqVO);
        ResourceServerDO server = resourceServerService.requireServer(id);
        return Result.ok(ResourceServerConvert.INSTANCE.convert(server));
    }

    /**
     * 获得线路机分页 (主表 + agent 运行时聚合: online state / agentVersion / xrayVersion / 流量 / throttle / configSyncState).
     *
     * <p>规范三步走: ① Convert 提 serverIds → ② Service 批量查 4 子表 + 跨模块查 syncState → ③ Convert 拼装 VO.
     *
     * @param reqVO 分页条件
     * @return 线路机列表项分页
     */
    @GetMapping("/page-frontline")
    public Result<PageResult<ServerFrontlineListItemRespVO>> getPage(@ModelAttribute ResourceServerPageReqVO reqVO) {
        PageResult<ResourceServerDO> page = resourceServerService.getServerPage(reqVO);
        Set<String> ids = ResourceServerFrontlineConvert.INSTANCE.extractServerIds(page.getRecords());
        ResourceServerFrontlineService.RuntimeBundle bundle = frontlineService.batchLoadRuntimeBundle(ids);
        Map<String, String> cfgSyncMap = agentRuntimeConfigApi.getSyncStateMap(ids);
        return Result.ok(ResourceServerFrontlineConvert.INSTANCE.convertPageWithRuntime(
                page, bundle.credentialMap(), bundle.runtimeMap(), bundle.capacityMap(),
                bundle.xrayMap(), cfgSyncMap, LocalDateTime.now()));
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
        return Result.ok(ResourceServerFrontlineConvert.INSTANCE.convert(frontlineService.get(id)));
    }

    /**
     * 获得线路机详情 (含 agent 运行时聚合; detail 页 header 用)
     *
     * @param id 线路机编号
     * @return 线路机详情
     */
    @GetMapping("/get-frontline-detail")
    public Result<ServerFrontlineListItemRespVO> getFrontlineDetail(@RequestParam("id") String id) {
        ResourceServerDO server = resourceServerService.requireServer(id);
        ResourceServerFrontlineService.RuntimeBundle bundle = frontlineService.loadRuntimeBundleSingle(id);
        Map<String, String> cfgSyncMap = agentRuntimeConfigApi.getSyncStateMap(Set.of(id));
        return Result.ok(ResourceServerFrontlineConvert.INSTANCE.convertSingleWithRuntime(
                server,
                bundle.credentialMap().get(id),
                bundle.runtimeMap().get(id),
                bundle.capacityMap().get(id),
                bundle.xrayMap().get(id),
                cfgSyncMap.get(id),
                LocalDateTime.now()));
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
        frontlineService.update(id, reqVO);
        return Result.ok(true);
    }
}
