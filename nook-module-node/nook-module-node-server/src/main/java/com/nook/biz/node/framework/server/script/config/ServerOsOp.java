package com.nook.biz.node.framework.server.script.config;

import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.framework.ssh.script.ScriptModule;

/**
 * 服务器 OS 调优 op 枚举; 每项独立运行, 跟 xray install 链路解耦.
 *
 * @author nook
 */
public enum ServerOsOp {

    /** 启用 swap (sizeMb 由调用方传入, 通过模板变量 SWAP_SIZE_MB 注入). */
    SWAP("swap", NookScripts.MODULE_SWAP),

    /** 启用 BBR 拥塞控制. */
    BBR("bbr", NookScripts.MODULE_BBR);

    private final String key;
    private final ScriptModule module;

    ServerOsOp(String key, ScriptModule module) {
        this.key = key;
        this.module = module;
    }

    public String key() {
        return key;
    }

    public ScriptModule module() {
        return module;
    }
}
