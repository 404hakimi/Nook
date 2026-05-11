package com.nook.biz.node.resource.api.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 服务器凭据 / 连接信息发生变化时发布; 监听方 (如 SSH 会话池) 据此清掉对应 server 的连接缓存,
 * 让下次操作走新凭据. resource_server update / delete 后统一发, update 不一定涉及凭据变化但简单起见全发.
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class ServerCredentialChangedEvent {

    private String serverId;
}
