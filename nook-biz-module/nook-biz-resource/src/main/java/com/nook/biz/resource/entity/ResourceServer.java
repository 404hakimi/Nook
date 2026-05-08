package com.nook.biz.resource.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 出口 VPS 服务器，与 backend 凭据合并存储。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_server")
public class ResourceServer extends BaseEntity {

    private String name;

    private String host;

    private Integer sshPort;

    private String sshUser;

    private String sshPassword;

    private String sshPrivateKey;

    /** SSH 命令最大耗时(秒)；建议 30-120 */
    private Integer sshTimeoutSeconds;

    /** "threexui" / "xray-grpc" */
    private String backendType;

    private String panelBaseUrl;

    private String panelUsername;

    private String panelPassword;

    /** 0=否 1=是；自签证书时设 1 */
    private Integer panelIgnoreTls;

    /** backend HTTP/gRPC 调用超时(秒)；建议 20-60 */
    private Integer backendTimeoutSeconds;

    private String xrayGrpcHost;

    private Integer xrayGrpcPort;

    private Integer totalBandwidth;

    private Integer totalIpCount;

    private String idcProvider;

    private String region;

    /** 1=运行 2=维护 3=下线 */
    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;
}
