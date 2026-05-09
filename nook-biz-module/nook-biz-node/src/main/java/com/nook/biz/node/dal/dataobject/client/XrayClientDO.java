package com.nook.biz.node.dal.dataobject.client;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** Xray 客户端凭据 (会员 × 线路 × 落地 IP); 对应表 xray_client. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xray_client")
public class XrayClientDO extends BaseEntity {

    private String serverId;

    private String ipId;

    private String memberUserId;

    /** 挂到的远端 Xray inbound tag (与 xray.json 里 inbound 的 tag 一致), 协议无关 */
    private String externalInboundRef;

    /** 协议编码: vmess / vless / trojan / shadowsocks; 与 XrayProtocol 枚举对齐 */
    private String protocol;

    private String transport;

    private String listenIp;

    private Integer listenPort;

    /** 协议级密钥 (vless/vmess UUID, trojan password) */
    private String clientUuid;

    /** 人类可读标识; 推荐格式 member_{memberId}_{ipId}, server 内全局唯一 */
    private String clientEmail;

    /** 1=运行 2=已停 3=待同步 4=远端已不存在 */
    private Integer status;

    private LocalDateTime lastSyncedAt;

    @TableLogic
    private Integer deleted;
}
