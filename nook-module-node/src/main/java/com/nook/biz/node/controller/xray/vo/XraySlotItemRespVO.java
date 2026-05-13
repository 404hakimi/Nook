package com.nook.biz.node.controller.xray.vo;

import lombok.Data;

/**
 * 管理后台 - Xray 节点 Slot 占用项 Resp VO
 *
 * <p>视图字段: 由 xray_slot_pool + xray_node + xray_client 三表 join 派生.
 *
 * @author nook
 */
@Data
public class XraySlotItemRespVO {

    /** 槽位编号 (1..slot_pool_size) */
    private Integer slotIndex;

    /** 客户连接端口 = xray_node.slot_port_base + slotIndex */
    private Integer listenPort;

    /** 是否被占用: 0=空闲 1=已占用 */
    private Integer used;

    /** 占用此槽的 client.id; 空闲时为 null */
    private String clientId;

    /** 客户邮箱 (xray_client.client_email); 空闲时为 null */
    private String clientEmail;

    /** 协议 (vless/vmess/trojan); 空闲时为 null */
    private String protocol;

    /** 传输 (tcp/ws/grpc/xhttp); 空闲时为 null */
    private String transport;

    /** Xray Client 状态: 1=运行 2=已停 3=待同步 4=远端已不存在; 空闲时为 null */
    private Integer clientStatus;
}
