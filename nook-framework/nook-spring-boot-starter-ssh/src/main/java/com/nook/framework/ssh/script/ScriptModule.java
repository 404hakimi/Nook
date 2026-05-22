package com.nook.framework.ssh.script;

import java.util.Set;

/**
 * 远端脚本元数据
 *
 * @author nook
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
