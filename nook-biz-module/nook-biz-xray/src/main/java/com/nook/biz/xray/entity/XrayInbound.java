package com.nook.biz.xray.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 一个会员在某 IP 上的 client 配置；与 3x-ui client / Xray inbound user 一一对应。
 * 表设计见 sql/05_xray.sql。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xray_inbound")
public class XrayInbound extends BaseEntity {

    private String serverId;

    private String ipId;

    private String memberUserId;

    /** Xray inbound 的 tag, 通常 "vmess-in" */
    private String externalInboundRef;

    /** vless / vmess / trojan / shadowsocks */
    private String protocol;

    private String transport;

    private String listenIp;

    private Integer listenPort;

    /** 协议级密钥(vless/vmess UUID, trojan password) */
    private String clientUuid;

    /** 人类可读标识；推荐格式 member_{memberId}_{ipId} */
    private String clientEmail;

    /** 1=运行 2=已停 3=待同步 4=远端已不存在 */
    private Integer status;

    private LocalDateTime lastSyncedAt;

    @TableLogic
    private Integer deleted;
}
