package com.nook.biz.resource.api.event;

/**
 * 服务器凭据/连接信息发生变化时发布。
 * 监听方(目前是 xray 模块的 XrayBackendFactory)应据此清掉对应 server 的连接缓存,
 * 让下次操作走新凭据。
 *
 * 触发时机：resource_server 行 update / delete 之后。
 *   - update 不一定都涉及凭据变化(如改 remark)，但简单起见统一发；
 *   - delete 后也发，让 backend 立即关闭旧连接释放资源。
 */
public record ServerCredentialChangedEvent(String serverId) { }
