package com.nook.biz.resource.controller.server.vo;

import lombok.Data;

/**
 * 服务器新增 / 编辑统一入参; Create 必填校验 + 全字段范围校验由 ResourceServerValidator 完成.
 *
 * @author nook
 */
@Data
public class ResourceServerSaveReqVO {

    private String name;

    private String host;

    private Integer sshPort;

    private String sshUser;

    private String sshPassword;

    /** SSH 会话握手超时(秒). */
    private Integer sshTimeoutSeconds;

    /** 单条命令最大耗时(秒). */
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传单文件超时(秒). */
    private Integer sshUploadTimeoutSeconds;

    /** 一次安装脚本最大耗时(秒). */
    private Integer installTimeoutSeconds;

    /** 月流量额度 GB; 不限留空. */
    private Integer monthlyTrafficGb;

    /** 带宽峰值 Mbps. */
    private Integer totalBandwidth;

    private String idcProvider;

    private String region;

    /** 1=运行 2=维护 3=下线. */
    private Integer status;

    private String remark;
}
