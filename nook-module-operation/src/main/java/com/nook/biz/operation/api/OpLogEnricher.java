package com.nook.biz.operation.api;

import com.nook.biz.operation.controller.vo.OpLogRespVO;

import java.util.List;

/**
 * op_log 友好名称补充 SPI; 业务模块按需实现, OpLogService 在 convert 后批量回填.
 *
 * <p>op_log 自己只存 ID (operator=adminId / serverId / targetId), 跨模块查名字需要业务侧给出实现:
 * <ul>
 *   <li>nook-module-system 实现 operatorName 补充 (admin → realName/username)</li>
 *   <li>nook-module-node   实现 serverName / targetName 补充 (server → name, client → email)</li>
 * </ul>
 *
 * <p>批量入参的目的是避开 N+1: 同一页所有 row 的 serverId 一次 IN 查出来.
 *
 * @author nook
 */
public interface OpLogEnricher {

    /**
     * 给一批 VO 填充 operatorName / serverName / targetName / opTypeLabel 等; 实现允许只填自己关心的字段.
     *
     * @param vos 待补充的 VO 列表; 实现内部就地修改, 不返回新列表
     */
    void enrich(List<OpLogRespVO> vos);
}
