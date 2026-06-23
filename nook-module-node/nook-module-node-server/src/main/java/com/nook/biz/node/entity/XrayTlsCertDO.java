package com.nook.biz.node.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Xray TLS 证书 DO (从 xray_install 抽出; 证书生命周期资产, 非协议参数, 跟 xray_install 1:1)
 *
 * @author nook
 */
@Data
@TableName("xray_tls_cert")
public class XrayTlsCertDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 服务器 id (主键, = xray_install.server_id). */
    @TableId(value = "server_id", type = IdType.INPUT)
    private String serverId;

    /** 证书对应 FQDN (冗余, 便于核对/续期重建). */
    private String fqdn;

    /** 后台签发的全链证书 PEM. */
    private String certPem;

    /** 证书私钥 PEM. */
    private String keyPem;

    /** 叶子证书到期时间; 续期扫描 + 复用判定据此. */
    private LocalDateTime notAfter;

    /** 创建时间. */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间. */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
