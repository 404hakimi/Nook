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
    SERVER_LIVE_DOMAIN_REQUIRED(5006, "上线 LIVE 前必须先填 domain"),
    // 5007 SERVER_SSH_PASSWORD_REQUIRED 已删除 — 由 ResourceServerSaveReqVOValidator 统一抛 PARAM_INVALID

    IP_TYPE_NOT_FOUND(5101, "IP 类型 %s 不存在"),
    IP_POOL_NOT_FOUND(5102, "IP 池条目 %s 不存在"),
    IP_POOL_IP_DUPLICATE(5103, "IP %s 已存在于池中"),
    IP_POOL_NOT_AVAILABLE(5104, "IP %s 当前不可分配 (状态: %s)"),
    IP_POOL_EXHAUSTED(5105, "%s 区域 %s 类型暂无可用 IP"),
    IP_POOL_OCCUPY_CONFLICT(5106, "IP %s 抢占失败, 已被其它会员领走"),
    IP_POOL_HAS_BOUND_CLIENT(5107, "IP %s 仍被客户端 %s 占用, 请先吊销该客户端再退订"),
    IP_POOL_NOT_SELF_DEPLOY(5108, "IP %s 部署模式为第三方, 不支持同步凭据 (仅自部署可用)"),
    IP_POOL_SOCKS5_INCOMPLETE(5109, "IP %s 的 SOCKS5 配置不完整 (port/user/password 必填)"),
    IP_POOL_SSH_CRED_MISSING(5110, "IP %s 缺 SSH 凭据 (运维操作需要), 请到编辑表单补全后再操作"),
    IP_POOL_LIFECYCLE_INVALID_TRANSITION(5111, "IP 池生命周期不允许 %s → %s"),
    ;

    private final int code;
    private final String message;
}
