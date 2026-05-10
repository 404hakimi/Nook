package com.nook.biz.resource.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务器资源表 (纯硬件 + SSH 凭据).
 *
 * <p>本表只管"机器活着 + SSH 通"; 任何 xray/business 相关字段都不应出现在这.
 * Xray 实例配置见 {@code xray_node} 表 (nook-biz-node 模块管).
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_server")
public class ResourceServer extends BaseEntity {

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

    /** 1=运行 2=维护 3=下线 */
    private Integer status;

    private String remark;
}
