package com.nook.biz.node.enums;

import com.nook.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Xray 模块错误码 (6xxx 段位)。 */
@Getter
@RequiredArgsConstructor
public enum XrayErrorCode implements ErrorCode {

    SERVER_CREDENTIAL_INVALID(6002, "服务器 %s 的 backend 凭据不完整"),
    BACKEND_UNREACHABLE(6003, "无法连接到服务器 %s"),
    BACKEND_AUTH_FAILED(6004, "服务器 %s 鉴权失败"),
    BACKEND_RESPONSE_INVALID(6005, "服务器 %s 响应非法: %s"),
    BACKEND_OPERATION_FAILED(6006, "服务器 %s 操作失败: %s"),
    REMOTE_INBOUND_NOT_FOUND(6007, "远端 inbound %s 不存在或未关联到任何 IP"),
    /** 远端 xray inbound 上找不到目标 client (rmi/rmu/lsi 阶段); 跟 DB 视角的 CLIENT_ENTITY_NOT_FOUND 区分 */
    CLIENT_NOT_FOUND(6008, "远端 xray 客户端 %s 不存在"),
    CLIENT_DUPLICATE(6009, "客户端 %s 已存在"),
    GRPC_NOT_IMPLEMENTED(6010, "gRPC backend 该操作未实现: %s"),
    /** 业务侧 (DB) 找不到该客户端; 用户通过 id 查询/操作但 id 错或已删 */
    CLIENT_ENTITY_NOT_FOUND(6011, "客户端 %s 不存在"),
    /** server 的 slot 池已满 (已用客户数 >= slot_pool_size); 需扩容或迁移到其它 server */
    SLOT_POOL_EXHAUSTED(6012, "server %s 的 slot 池已满, 无空闲槽位"),
    /** server 的 nook 状态记录不存在; 通常说明该 server 还没通过 nook 部署过 */
    SERVER_STATE_NOT_FOUND(6013, "server %s 的 nook 状态记录不存在"),
    /** provision 入参跨字段校验失败 (如 flow 跟 protocol 不匹配 / expiry 已过期 / limitIp 超上限) */
    CLIENT_PROVISION_INVALID(6014, "客户端开通参数非法: %s"),
    /** IP 已被其他 client 占用; 跟 xray_client.uk_ip_id UNIQUE 约束对齐 */
    CLIENT_IP_ALREADY_USED(6015, "IP %s 已被其他客户端占用, 不能重复 provision"),
    ;

    private final int code;
    private final String message;
}
