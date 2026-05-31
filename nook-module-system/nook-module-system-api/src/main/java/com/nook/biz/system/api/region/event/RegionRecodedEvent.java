package com.nook.biz.system.api.region.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 区域码更正事件: 区域字典 code 改名后由 system 模块发布.
 *
 * <p>引用该码的各模块各自监听并把自己表里的旧码迁到新码:
 * node → resource_server.region; trade → trade_plan.region_code.
 * 同步监听, 与发布方同一事务, 任一失败整体回滚 (区域码不会半迁移).
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
