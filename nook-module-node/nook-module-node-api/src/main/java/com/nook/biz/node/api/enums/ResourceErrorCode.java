package com.nook.biz.node.api.enums;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Resource 模块错误码枚举
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
public enum ResourceErrorCode implements ErrorCode {

    SERVER_NOT_FOUND(5001, "服务器 %s 不存在"),
    SERVER_NAME_DUPLICATE(5002, "服务器别名 %s 已存在"),
    SERVER_HOST_DUPLICATE(5003, "服务器主机 %s 已存在"),
    SERVER_DOMAIN_DUPLICATE(5004, "服务器域名 %s 已被占用"),
    SERVER_LIFECYCLE_INVALID_TRANSITION(5005, "服务器生命周期不允许 %s → %s"),
    SERVER_LIVE_DOMAIN_REQUIRED(5006, "上线前必须先填域名"),
    SERVER_SSH_LOCKED_AFTER_LIVE(5007, "服务器运行中不可改 SSH 端口 (需先退到待上线)"),
    SERVER_HAS_BOUND_CLIENT(5008, "服务器 %s 仍被客户端绑定, 请先吊销/退订占用它的订阅再删除"),
    SERVER_REGION_LOCKED(5009, "服务器上线后不可修改区域; 区域是套餐与线路机 / 落地机的匹配依据, 如需变更请退役后新建"),

    LANDING_NOT_FOUND(5102, "落地节点 %s 不存在"),
    LANDING_SOCKS5_INCOMPLETE(5103, "落地节点 %s 的 SOCKS5 业务配置未填全"),
    LANDING_SSH_CRED_MISSING(5104, "落地节点 %s 缺 SSH 凭据"),
    LANDING_IP_DUPLICATE(5105, "落地节点 IP %s 已存在"),
    LANDING_HAS_BOUND_CLIENT(5106, "落地节点 %s 仍被客户端 %s 占用, 请先吊销该客户端再删除"),
    LANDING_IN_USE_CANNOT_RETIRE(5107, "落地机仍被客户端占用, 不能停用; 请先释放占用它的订阅 / 客户端"),
    LANDING_IP_TYPE_INVALID(5108, "IP 类型 %s 不存在"),
    ;

    private final int code;
    private final String message;
}
