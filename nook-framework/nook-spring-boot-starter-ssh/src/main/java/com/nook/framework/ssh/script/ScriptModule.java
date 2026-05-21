package com.nook.framework.ssh.script;

import java.util.Set;

/**
 * 一份远端脚本的元数据: 路径 + 必填 placeholder 集 + tmp 前缀 + 描述.
 *
 * <p>业务模块用 record 常量持有 (例 NookScripts.NOOK_AGENT_INSTALL), 启动时调
 * {@link ScriptCatalog#register(ScriptModule)} 注册进 catalog.
 *
 * @param id           catalog 唯一 id (kebab-case)
 * @param category     分类
 * @param classpath    classpath 上的 .sh / .sh.tmpl 路径
 * @param tmpPrefix    上传到远端 /tmp 时的文件名前缀
 * @param description  一行描述, 列表 UI 用
 * @param requiredVars 必填 {{VAR}} 集; render 前 catalog 校验缺一抛错
 */
public record ScriptModule(
        String id,
        ScriptCategory category,
        String classpath,
        String tmpPrefix,
        String description,
        Set<String> requiredVars
) {
}
