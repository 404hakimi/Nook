package com.nook.biz.xray.api;

import com.nook.biz.xray.api.dto.ClientProvisionRequestDTO;
import com.nook.biz.xray.api.dto.ClientResultDTO;
import com.nook.biz.xray.api.dto.ClientTrafficDTO;

/**
 * 提供给 member / business 等 biz 模块的客户端 provision/revoke 接口。
 * 跨模块只暴露 api 包；service / mapper / entity 都不可见。
 */
public interface XrayClientApi {

    /** 业务侧触发开通(订阅成功后)；幂等：重复请求同一 (memberUserId, ipId) 抛错。 */
    ClientResultDTO provision(ClientProvisionRequestDTO req);

    /** 吊销；inboundEntityId 不存在抛错。 */
    void revoke(String inboundEntityId);

    /** 拉单条 client 的实时流量与配额状态。 */
    ClientTrafficDTO getTraffic(String inboundEntityId);
}
