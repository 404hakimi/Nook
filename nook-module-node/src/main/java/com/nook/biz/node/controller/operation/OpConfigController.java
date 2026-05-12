package com.nook.biz.node.controller.operation;

import com.nook.biz.node.controller.operation.vo.OpConfigRespVO;
import com.nook.biz.node.controller.operation.vo.OpConfigSaveReqVO;
import com.nook.biz.node.convert.operation.OpConfigConvert;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import com.nook.biz.operation.service.OpConfigService;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping
    public Result<List<OpConfigRespVO>> getOpConfigList() {
        List<OpConfigDO> list = opConfigService.getOpConfigList();
        return Result.ok(OpConfigConvert.INSTANCE.convertList(list));
    }

    @GetMapping("/{id}")
    public Result<OpConfigRespVO> getOpConfig(@PathVariable("id") String id) {
        OpConfigDO entity = opConfigService.getOpConfig(id);
        return Result.ok(OpConfigConvert.INSTANCE.convert(entity));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateOpConfig(@PathVariable("id") String id,
                                          @Valid @RequestBody OpConfigSaveReqVO updateReqVO) {
        opConfigService.updateOpConfig(id,
                updateReqVO.getExecTimeoutSeconds(),
                updateReqVO.getWaitTimeoutSeconds(),
                updateReqVO.getMaxRetry(),
                updateReqVO.getEnabled(),
                updateReqVO.getDescription());
        return Result.ok(true);
    }

    @PostMapping("/{id}/reset")
    public Result<Boolean> resetOpConfig(@PathVariable("id") String id) {
        opConfigService.resetOpConfig(id);
        return Result.ok(true);
    }
}
