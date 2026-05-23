package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务器资源 DO (核心字段; SSH 凭据 / 账面 / DNS 拆到 1:1 子表).
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_server")
public class ResourceServerDO extends BaseEntity {

    private String name;

    /** 装机生命周期; 取值见 {@link ResourceServerLifecycleEnum}. */
    private String lifecycleState;

    private Integer totalIpCount;

    /** 区域码; FK → resource_region.code. */
    private String region;

    private String remark;

    @TableLogic
    private Integer deleted;

    /** Agent 鉴权 token; 装机时生成, agent push 接口校验 X-Agent-Token 必须等于这个值. */
    private String agentToken;
}
