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

    private String serverId;

    private String ipId;

    private String memberUserId;

    /** 协议级密钥 (vless/vmess UUID, trojan password) */
    private String clientUuid;

    /** 人类可读标识; 推荐格式 member_{memberId}_{ipId}, server 内全局唯一 */
    private String clientEmail;

    /** 流量上限字节; 0/null=不限 (写 xray client totalBytes; reconcile 期望态用). */
    private Long totalBytes;

    /** 客户端状态 {@link XrayClientStatusEnum} */
    private Integer status;

    private LocalDateTime lastSyncedAt;
}
