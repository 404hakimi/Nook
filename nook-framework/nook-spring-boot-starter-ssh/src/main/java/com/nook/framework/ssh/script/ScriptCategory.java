package com.nook.framework.ssh.script;

/**
 * 远端脚本分类枚举
 *
 * @author nook
 */
public enum ScriptCategory {
    /** 一键装机. */
    INSTALL,
    /** 卸载脚本. */
    UNINSTALL,
    /** 运维短操作 (改密 / 切日志 / OS 调优). */
    OPS,
    /** 可复用拼装模块. */
    MODULE
}
