package com.nook.biz.node.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Xray 装机状态; 后台"配置落库 → 通知 agent 装机"的结果状态机
 *
 * @author nook
 */
@Getter
@AllArgsConstructor
public enum XrayInstallStatusEnum {

    /** 配置已落库, 已通知 agent, 等回报. */
    DEPLOYING("deploying", "部署中"),
    /** agent 回报装机成功. */
    OK("ok", "已部署"),
    /** agent 回报失败 / 通知失败 (配置仍留库, 重新部署幂等修复). */
    FAILED("failed", "部署失败"),
    ;

    /** DB 存储值. */
    private final String code;

    /** 展示标签. */
    private final String label;
}
