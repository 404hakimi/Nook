package com.nook.biz.node.dal.dataobject.resource;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 服务器流量周期归档 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_server_traffic")
public class ResourceServerTrafficDO extends BaseEntity {

    /** 所属服务器. */
    private String serverId;

    /** 周期起点(我方重置日). */
    private LocalDate periodStart;

    /** 周期终点(归档时 = 下一周期起点). */
    private LocalDate periodEnd;

    /** 该周期入站字节. */
    private Long rxBytes;

    /** 该周期出站字节. */
    private Long txBytes;

    /** 该周期已用 = 入站 + 出站. */
    private Long usedBytes;
}
