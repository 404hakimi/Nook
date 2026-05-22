package com.nook.biz.node.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 服务器凭据变更事件; resource_server update / delete 后发布.
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class ServerCredentialChangedEvent {

    private String serverId;
}
