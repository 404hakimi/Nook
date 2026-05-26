package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务器资源 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_server")
public class ResourceServerDO extends BaseEntity {

    /** agent 角色 {@link ResourceServerTypeEnum} */
    private String serverType;

    private String name;

    /** 出网真实 IP; landing 必填, frontline 选填 (出口 IP). */
    private String ipAddress;

    /** 装机生命周期 {@link ResourceServerLifecycleEnum} */
    private String lifecycleState;

    private Integer totalIpCount;

    /** 区域码; FK → system_region.code. */
    private String region;

    private String remark;

    @TableLogic
    private Integer deleted;

    /** Agent 鉴权 token; 装机时生成, agent push 接口校验 X-Agent-Token 必须等于这个值. */
    private String agentToken;
}
