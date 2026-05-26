package com.nook.biz.operation.controller.admin;

import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.operation.controller.admin.vo.OpLogPageReqVO;
import com.nook.biz.operation.controller.admin.vo.OpLogRespVO;
import com.nook.biz.operation.convert.OpLogConvert;
import com.nook.biz.operation.dal.dataobject.OpLogDO;
import com.nook.biz.operation.service.OpLogService;
import com.nook.biz.system.api.user.SystemUserApi;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - 操作日志 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/operation/op-log")
@Validated
@RequiredArgsConstructor
public class OpLogController {

    private final OpLogService opLogService;
    private final ResourceServerApi resourceServerApi;
    private final SystemUserApi systemUserApi;

    /**
     * 获得操作日志分页
     *
     * @param pageReqVO 分页条件
     * @return 操作日志分页 (含 server / 操作人 友好名; target 暂存原 id, 后续走 OpLogTargetNameResolver SPI)
     */
    @GetMapping("/page-op-log")
    public Result<PageResult<OpLogRespVO>> getOpLogPage(@ModelAttribute OpLogPageReqVO pageReqVO) {
        PageResult<OpLogDO> pageResult = opLogService.page(pageReqVO.getPageNo(), pageReqVO.getPageSize(),
                pageReqVO.getStatus(), pageReqVO.getServerId(), pageReqVO.getOpType());
        List<OpLogDO> rows = pageResult.getRecords();

        Set<String> serverIds = OpLogConvert.extractServerIds(rows);
        Set<String> operatorIds = OpLogConvert.extractOperatorIds(rows);
        Map<String, String> serverNames = resourceServerApi.getServerNameMap(serverIds);
        Map<String, String> operatorNames = systemUserApi.getUserNameMap(operatorIds);
        // target name 暂留 null, OpLogConvert 内 fallback 到 targetId
        return Result.ok(OpLogConvert.INSTANCE.convertPageWithInfo(pageResult,
                serverNames, operatorNames, Collections.emptyMap()));
    }

    /**
     * 获得操作日志详情
     *
     * @param id 操作日志编号
     * @return 操作日志详情
     */
    @GetMapping("/get-op-log")
    public Result<OpLogRespVO> getOpLog(@RequestParam("id") String id) {
        OpLogDO entity = opLogService.findById(id);
        List<OpLogDO> single = Collections.singletonList(entity);
        Map<String, String> serverNames = resourceServerApi.getServerNameMap(OpLogConvert.extractServerIds(single));
        Map<String, String> operatorNames = systemUserApi.getUserNameMap(OpLogConvert.extractOperatorIds(single));
        return Result.ok(OpLogConvert.INSTANCE.convertForDetailWithInfo(entity,
                serverNames, operatorNames, Collections.emptyMap()));
    }

    /**
     * 取消排队中的操作
     *
     * @param id 操作日志编号
     * @return 是否取消成功
     */
    @PostMapping("/cancel-op-log")
    public Result<Boolean> cancelOpLog(@RequestParam("id") String id) {
        boolean cancelled = opLogService.cancelQueued(id);
        return Result.ok(cancelled);
    }
}
