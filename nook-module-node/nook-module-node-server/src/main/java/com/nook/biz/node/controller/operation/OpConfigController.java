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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 管理后台 - Op 配置 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/operation/op-config")
@Validated
@RequiredArgsConstructor
public class OpConfigController {

    private final OpConfigService opConfigService;

    /**
     * 获得 op 配置列表
     *
     * @return op 配置列表
     */
    @GetMapping("/list")
    public Result<List<OpConfigRespVO>> getOpConfigList() {
        List<OpConfigDO> list = opConfigService.getOpConfigList();
        return Result.ok(OpConfigConvert.INSTANCE.convertList(list));
    }

    /**
     * 获得精简 op 配置列表 (opType + name); OpLog 页面下拉 / 名称回填用
     *
     * @return 精简 op 配置列表
     */
    @GetMapping("/simple-list")
    public Result<List<OpConfigSimpleRespVO>> getSimpleOpConfigList() {
        List<OpConfigDO> list = opConfigService.getOpConfigList();
        return Result.ok(OpConfigConvert.INSTANCE.convertSimpleList(list));
    }

    /**
     * 获得 OpType 下拉选项 (含是否已配置标记)
     *
     * @return OpType 下拉选项
     */
    @GetMapping("/op-type-list")
    public Result<List<OpTypeOptionRespVO>> getOpTypeOptionList() {
        Set<String> configured = new HashSet<>();
        for (OpConfigDO row : opConfigService.getOpConfigList()) {
            configured.add(row.getOpType());
        }
        List<OpTypeOptionRespVO> options = Stream.of(OpType.values())
                .map(t -> new OpTypeOptionRespVO(t.name(), configured.contains(t.name())))
                .toList();
        return Result.ok(options);
    }

    /**
     * 获得 op 配置详情
     *
     * @param id op 配置编号
     * @return op 配置详情
     */
    @GetMapping("/get")
    public Result<OpConfigRespVO> getOpConfig(@RequestParam("id") String id) {
        OpConfigDO entity = opConfigService.getOpConfig(id);
        return Result.ok(OpConfigConvert.INSTANCE.convert(entity));
    }

    /**
     * 创建 op 配置
     *
     * @param createReqVO 创建入参
     * @return op 配置编号
     */
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

    /**
     * 更新 op 配置
     *
     * @param id          op 配置编号
     * @param updateReqVO 更新入参
     * @return 是否成功
     */
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

    /**
     * 删除 op 配置
     *
     * @param id op 配置编号
     * @return 是否成功
     */
    @DeleteMapping("/delete")
    public Result<Boolean> deleteOpConfig(@RequestParam("id") String id) {
        opConfigService.deleteOpConfig(id);
        return Result.ok(true);
    }
}
