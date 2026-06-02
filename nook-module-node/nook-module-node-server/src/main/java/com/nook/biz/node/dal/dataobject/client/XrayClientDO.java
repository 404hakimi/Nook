package com.nook.biz.node.dal.dataobject.client;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.XrayClientStatusEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Xray 客户端 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xray_client")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class XrayClientDO extends BaseEntity {

    /** 所属线路机 server id. */
    private String serverId;

    /** 绑定的落地 IP id. */
    private String ipId;

    /** 所属会员. */
    private String memberUserId;

    /** 协议级密钥 (vless/vmess UUID, trojan password) */
    private String clientUuid;

    /** 人类可读标识; 格式 member_{memberId}_{clientId}, server 内全局唯一; clientId 创建后不变, 跨轮换/换机稳定 */
    private String clientEmail;

    /** 客户端状态 {@link XrayClientStatusEnum} */
    private Integer status;

    /** 最近一次与远端 xray 对账成功时间. */
    private LocalDateTime lastSyncedAt;
}
