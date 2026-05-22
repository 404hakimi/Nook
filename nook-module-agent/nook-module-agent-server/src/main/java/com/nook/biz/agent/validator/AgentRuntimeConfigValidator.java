package com.nook.biz.agent.validator;

import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Map;

/**
 * Agent 运行时配置业务校验
 *
 * @author nook
 */
@Component
public class AgentRuntimeConfigValidator {

    private static final Yaml YAML = new Yaml();

    /**
     * 校验 yaml: 非空 + 语法合法 + 顶层 object.
     *
     * @param yaml 待校验 yaml 文本
     */
    public void validateYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "yaml 不能为空");
        }
        try {
            Object parsed = YAML.load(yaml);
            if (!(parsed instanceof Map)) {
                throw new BusinessException(CommonErrorCode.PARAM_INVALID, "yaml 顶层必须是 object");
            }
        } catch (YAMLException ye) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "yaml 语法错误: " + ye.getMessage());
        }
    }
}
