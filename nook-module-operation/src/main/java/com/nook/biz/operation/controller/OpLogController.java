package com.nook.biz.operation.controller;

import com.nook.biz.operation.controller.vo.OpLogPageReqVO;
import com.nook.biz.operation.controller.vo.OpLogRespVO;
import com.nook.biz.operation.service.OpLogService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * op_log 可视化接口; 只读 + 取消 QUEUED.
 *
 * <p>写路径 (入队 / 执行) 在各业务模块自己的 controller 里, 通过 service 走 OperationOrchestrator.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/operation/op-log")
public class OpLogController {

    @Resource
    private OpLogService opLogService;

    @GetMapping
    public Result<PageResult<OpLogRespVO>> page(@ModelAttribute OpLogPageReqVO reqVO) {
        return Result.ok(opLogService.page(reqVO));
    }

    @GetMapping("/{id}")
    public Result<OpLogRespVO> detail(@PathVariable String id) {
        return Result.ok(opLogService.findById(id));
    }

    /**
     * 仅取消 QUEUED 的 op; RUNNING 不支持. data=true 表示真的取消了, false = 已非 QUEUED 状态 (前端可刷新看实时状态).
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> cancel(@PathVariable String id) {
        return Result.ok(opLogService.cancelQueued(id));
    }
}
