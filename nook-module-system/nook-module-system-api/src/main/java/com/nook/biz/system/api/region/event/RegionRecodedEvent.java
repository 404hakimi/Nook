package com.nook.biz.system.api.region.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 区域码更正事件; 引用方同步监听迁移自己的旧码引用, 与发布方同一事务, 任一失败整体回滚
 *
 * @author nook
 */
@Data
@AllArgsConstructor
public class RegionRecodedEvent {

    /** 原区域码. */
    private String oldCode;

    /** 新区域码. */
    private String newCode;
}
