package com.nook.biz.node.controller.operation;

import com.nook.biz.node.controller.operation.vo.OpConfigCreateReqVO;
import com.nook.biz.node.controller.operation.vo.OpConfigRespVO;
import com.nook.biz.node.controller.operation.vo.OpConfigSaveReqVO;
import com.nook.biz.node.controller.operation.vo.OpConfigSimpleRespVO;
import com.nook.biz.node.controller.operation.vo.OpTypeOptionRespVO;
import com.nook.biz.node.convert.operation.OpConfigConvert;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import com.nook.biz.operation.service.OpConfigService;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 管理后台 - Op 配置
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/operation/op-config")
@Validated
public class OpConfigController {

    @Resource
    private OpConfigService opConfigService;

    @GetMapping("/list")
    public Result<List<OpConfigRespVO>> getOpConfigList() {
        List<OpConfigDO> list = opConfigService.getOpConfigList();
        return Result.ok(OpConfigConvert.INSTANCE.convertList(list));
    }

    /** 精简列表: 仅 opType + name; 给 OpLog 等页面做下拉 / 名称回填用 */
    @GetMapping("/simple-list")
    public Result<List<OpConfigSimpleRespVO>> getSimpleOpConfigList() {
        List<OpConfigDO> list = opConfigService.getOpConfigList();
        return Result.ok(OpConfigConvert.INSTANCE.convertSimpleList(list));
    }

    @GetMapping("/op-type-list")
    public Result<List<OpTypeOptionRespVO>> getOpTypeOptionList() {
        // 给前端 Create 弹框提供 OpType 下拉 + 是否已配置标记
        Set<String> configured = new HashSet<>();
        for (OpConfigDO row : opConfigService.getOpConfigList()) {
            configured.add(row.getOpType());
        }
        List<OpTypeOptionRespVO> options = Stream.of(OpType.values())
                .map(t -> new OpTypeOptionRespVO(t.name(), configured.contains(t.name())))
                .toList();
        return Result.ok(options);
    }

    @GetMapping("/get")
    public Result<OpConfigRespVO> getOpConfig(@RequestParam("id") String id) {
        OpConfigDO entity = opConfigService.getOpConfig(id);
        return Result.ok(OpConfigConvert.INSTANCE.convert(entity));
    }

    @PostMapping("/create")
    public Result<String> createOpConfig(@Valid @RequestBody OpConfigCreateReqVO createReqVO) {
        String id = opConfigService.createOpConfig(
                createReqVO.getOpType(),
                createReqVO.getName(),
                createReqVO.getExecTimeoutSeconds(),
                createReqVO.getWaitTimeoutSeconds(),
                createReqVO.getMaxRetry(),
                createReqVO.getEnabled(),
                createReqVO.getDescription());
        return Result.ok(id);
    }

    @PutMapping("/update")
    public Result<Boolean> updateOpConfig(@RequestParam("id") String id,
                                          @Valid @RequestBody OpConfigSaveReqVO updateReqVO) {
        opConfigService.updateOpConfig(id,
                updateReqVO.getName(),
                updateReqVO.getExecTimeoutSeconds(),
                updateReqVO.getWaitTimeoutSeconds(),
                updateReqVO.getMaxRetry(),
                updateReqVO.getEnabled(),
                updateReqVO.getDescription());
        return Result.ok(true);
    }

    @DeleteMapping("/delete")
    public Result<Boolean> deleteOpConfig(@RequestParam("id") String id) {
        opConfigService.deleteOpConfig(id);
        return Result.ok(true);
    }
}
