package com.nook.biz.node.framework.xray.inbound;

import lombok.Builder;
import lombok.Data;

/**
 * 协议装机表单的单个字段描述; 各协议 {@link InboundProtocol#formSchema()} 声明, 前端据此动态渲染 + 校验。
 *
 * <p>加协议 = 协议自己声明字段 schema, 前端通用动态表单零改 (字段类型落在已支持 widget 集合内时)。
 *
 * @author nook
 */
@Data
@Builder
public class InboundFieldSchema {

    /** 字段键 (= 提交时 params 里的 key, 也 = formPrefill 回填 key). */
    private String name;

    /** 显示名. */
    private String label;

    /** 控件类型: text / select / number. */
    private String type;

    /** 静态必填. */
    private boolean required;

    /** 条件必填: 当此字段非空时本字段才必填 (如 subdomain 当 domainId 选了); 空 = 仅看 required. */
    private String requiredWhenField;

    /** 默认值 (新装预填). */
    private Object defaultValue;

    /** 占位提示. */
    private String placeholder;

    /** 校验正则 (type=text). */
    private String pattern;

    /** select 候选来源 key; 前端 loader 注册表解析 (domains / realityDest). */
    private String optionsKey;

    /** select 是否允许自定义输入 (如 realityDest 可填预设外的主机名). */
    private boolean allowCustom;
}
