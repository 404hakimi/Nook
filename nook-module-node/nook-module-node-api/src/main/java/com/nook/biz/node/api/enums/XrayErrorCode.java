package com.nook.biz.node.api.enums;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Xray 模块错误码枚举
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
public enum XrayErrorCode implements ErrorCode {

    BACKEND_OPERATION_FAILED(6006, "服务器 %s 操作失败: %s"),
    /** 远端 xray inbound 上找不到目标 client (rmi/rmu/lsi 阶段); 跟 DB 视角的 CLIENT_ENTITY_NOT_FOUND 区分 */
    CLIENT_NOT_FOUND(6008, "远端 xray 客户端 %s 不存在"),
    CLIENT_DUPLICATE(6009, "客户端 %s 已存在"),
    /** 业务侧 (DB) 找不到该客户端; 用户通过 id 查询/操作但 id 错或已删 */
    CLIENT_ENTITY_NOT_FOUND(6011, "客户端 %s 不存在"),
    /** server 的 nook 状态记录不存在; 通常说明该 server 还没通过 nook 部署过 */
    SERVER_STATE_NOT_FOUND(6013, "服务器 %s 的 Xray 安装记录不存在"),
    /** provision 入参跨字段校验失败 (如 flow 跟 protocol 不匹配 / expiry 已过期 / limitIp 超上限) */
    CLIENT_PROVISION_INVALID(6014, "客户端开通参数非法: %s"),
    /** IP 已被其他客户端占用 */
    CLIENT_IP_ALREADY_USED(6015, "IP %s 已被其他客户端占用, 不能重复开通"),
    /** 重装 xray 时改了客户面连接参数 (port / wsPath / domain), 现有客户 URL 会失效, 拒绝变更 */
    NODE_PARAM_CHANGE_BLOCKED(6017, "服务器 %s 有 %s 个在用客户, 客户端连接参数变更被拒: %s; 请先吊销全部客户或保持参数不变"),
    /** 装机入参跨字段校验失败 (如 useTls=true 但 domain / tls 路径缺失) */
    SERVER_INSTALL_INVALID(6018, "xray 装机参数非法: %s"),
    ;

    private final int code;
    private final String message;
}
