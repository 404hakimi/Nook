package com.nook.biz.node.framework.server.script.config;

/**
 * 服务器 OS 调优 op 枚举; 每项独立运行, 跟 xray install 链路解耦.
 *
 * @author nook
 */
public enum ServerOsOp {

    /** 启用 swap (sizeMb 由调用方传入, 通过模板变量 SWAP_SIZE_MB 注入). */
    SWAP("swap", RemoteScriptPaths.INSTALL_MODULES_DIR + "20-swap.sh.tmpl"),

    /** 启用 BBR 拥塞控制. */
    BBR("bbr", RemoteScriptPaths.INSTALL_MODULES_DIR + "30-bbr.sh.tmpl");

    /** op key, 拼远端 tmp 脚本名 + 日志区分用; 小写, 短横线分隔. */
    private final String key;

    /** classpath 上的模块脚本路径; 由 RemoteScriptRunner 渲染 + 上传 + 跑. */
    private final String modulePath;

    ServerOsOp(String key, String modulePath) {
        this.key = key;
        this.modulePath = modulePath;
    }

    public String key() {
        return key;
    }

    public String modulePath() {
        return modulePath;
    }
}
