package com.nook.biz.node.controller.resource;

import com.nook.biz.node.config.WebStreamingProperties;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerSaveReqVO;
import com.nook.biz.node.convert.resource.ResourceServerConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.agent.AgentInstallScriptService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import com.nook.framework.web.StreamingEndpointSupport;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;

/**
 * 管理后台 - 资源服务器
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server")
@Validated
public class ResourceServerController {

    @Resource
    private ResourceServerService resourceServerService;
    @Resource
    private ResourceServerValidator serverValidator;
    @Resource
    private AgentInstallScriptService agentInstallScriptService;
    @Resource
    private StreamingEndpointSupport streamingSupport;
    @Resource
    private WebStreamingProperties webStreamingProperties;

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

    /**
     * SSH 自动装 nook-agent; 复用 resource_server 已存 SSH 凭据, 流式日志走 ResponseBodyEmitter.
     * 重置 agent_token 后跑 install/nook-agent.sh.tmpl → agent active.
     */
    @PostMapping(value = "/agent-install", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter agentInstall(@RequestParam("id") String id,
                                            @RequestParam(value = "role", defaultValue = "frontline") String role) {
        int installTimeout = serverValidator.validateExists(id).getInstallTimeoutSeconds();
        Duration emitterTimeout = Duration.ofSeconds(installTimeout).plus(webStreamingProperties.getEmitterBuffer());
        return streamingSupport.stream("agent-install:" + id, emitterTimeout,
                lineSink -> agentInstallScriptService.installStreaming(id, role, lineSink));
    }
}
