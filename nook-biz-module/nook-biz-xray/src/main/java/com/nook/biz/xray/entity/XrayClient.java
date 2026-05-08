package com.nook.biz.xray.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 一个会员在某 (线路, 落地 IP) 上的 Xray client 凭据 — 表设计见 sql/05_xray.sql。
 * 与 Xray 协议层的 client (vmess/vless/trojan inbound 下的 user 条目) 一一对应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xray_client")
public class XrayClient extends BaseEntity {

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
