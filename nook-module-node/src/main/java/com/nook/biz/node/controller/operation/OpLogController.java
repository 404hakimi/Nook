package com.nook.biz.node.controller.operation;

import com.nook.biz.node.controller.operation.vo.OpLogPageReqVO;
import com.nook.biz.node.controller.operation.vo.OpLogRespVO;
import com.nook.biz.node.convert.operation.OpLogConvert;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.client.XrayClientService;
import com.nook.biz.operation.dal.dataobject.OpLogDO;
import com.nook.biz.operation.service.OpLogService;
import com.nook.biz.system.service.SystemUserService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - 操作日志
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/operation/op-log")
@Validated
public class OpLogController {

    @Resource
    private OpLogService opLogService;
    @Resource
    private ResourceServerService resourceServerService;
    @Resource
    private SystemUserService systemUserService;
    @Resource
    private XrayClientService xrayClientService;

    @GetMapping
    public Result<PageResult<OpLogRespVO>> getOpLogPage(@ModelAttribute OpLogPageReqVO pageReqVO) {
        PageResult<OpLogDO> pageResult = opLogService.page(pageReqVO.getPageNo(), pageReqVO.getPageSize(),
                pageReqVO.getStatus(), pageReqVO.getServerId(), pageReqVO.getOpType());
        List<OpLogDO> rows = pageResult.getRecords();

        // 批量回填名称: server / 操作人 / target (xray_client.email)
        Set<String> serverIds = OpLogConvert.extractServerIds(rows);
        Set<String> operatorIds = OpLogConvert.extractOperatorIds(rows);
        Set<String> targetIds = OpLogConvert.extractTargetIds(rows);
        Map<String, String> serverNames = resourceServerService.getServerNameMap(serverIds);
        Map<String, String> operatorNames = systemUserService.loadUserNameMap(operatorIds);
        Map<String, String> targetNames = xrayClientService.getEmailMap(targetIds);

        return Result.ok(OpLogConvert.INSTANCE.convertPageWithInfo(pageResult,
                serverNames, operatorNames, targetNames));
    }

    @GetMapping("/{id}")
    public Result<OpLogRespVO> getOpLog(@PathVariable("id") String id) {
        OpLogDO entity = opLogService.findById(id);
        List<OpLogDO> single = Collections.singletonList(entity);
        Map<String, String> serverNames = resourceServerService.getServerNameMap(OpLogConvert.extractServerIds(single));
        Map<String, String> operatorNames = systemUserService.loadUserNameMap(OpLogConvert.extractOperatorIds(single));
        Map<String, String> targetNames = xrayClientService.getEmailMap(OpLogConvert.extractTargetIds(single));
        return Result.ok(OpLogConvert.INSTANCE.convertForDetailWithInfo(entity,
                serverNames, operatorNames, targetNames));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> cancelOpLog(@PathVariable("id") String id) {
        boolean cancelled = opLogService.cancelQueued(id);
        return Result.ok(cancelled);
    }
}
