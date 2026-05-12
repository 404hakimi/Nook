package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.enums.ResourceServerStatusEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务器资源 DO (纯硬件 + SSH 凭据).
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_server")
public class ResourceServerDO extends BaseEntity {

    private String name;

    private String host;

    private Integer sshPort;

    private String sshUser;

    private String sshPassword;

    /** SSH 会话握手超时(秒); 建议 30-120 */
    private Integer sshTimeoutSeconds;

    /** SSH 单条命令最大耗时(秒); 跨洲建议 30-60 */
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传单文件超时(秒); 跨洲带宽差时调高, 建议 30-120 */
    private Integer sshUploadTimeoutSeconds;

    /** 装/重装一次脚本最大耗时(秒); HTTP Emitter 端 = 此值 + 60s */
    private Integer installTimeoutSeconds;

    /** 带宽峰值 Mbps */
    private Integer totalBandwidth;

    /** 月流量额度 GB; null/0 表示不限或未配置 */
    private Integer monthlyTrafficGb;

    private Integer totalIpCount;

    private String idcProvider;

    private String region;

    /** 状态; 取值见 {@link ResourceServerStatusEnum} */
    private Integer status;

    private String remark;
}
