package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;

import java.util.Collection;
import java.util.List;

/**
 * 落地机概要查询 Api (跨模块; trade 算 SKU 池容量 + 绑定校验用).
 *
 * @author nook
 */
public interface ResourceServerLandingApi {

    /**
     * 批量查落地机概要 (主表 lifecycle + landing 子表 status/ipType/ipAddress).
     *
     * @param serverIds 落地机 server id 集合
     * @return 概要列表 (不存在的 id 跳过)
     */
    List<LandingSummaryDTO> listSummaryByServerIds(Collection<String> serverIds);
}
