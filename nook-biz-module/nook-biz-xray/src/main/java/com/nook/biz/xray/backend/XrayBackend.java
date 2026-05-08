package com.nook.biz.xray.backend;

import com.nook.biz.xray.backend.dto.XrayClientRef;
import com.nook.biz.xray.backend.dto.XrayClientSpec;
import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;

import java.util.List;

/**
 * Xray 操作的统一抽象。当前唯一实现是 {@link com.nook.biz.xray.backend.grpc.XrayGrpcBackend}，
 * 接口保留是为单元测试可注入 mock 实现 + 将来万一接入新 backend(如 sing-box gRPC) 可以平滑切换。
 *
 * 设计原则:
 *   1. 一个 backend 实例绑定一台 resource_server, 所有方法对该 server 操作.
 *   2. 失败统一抛 {@link com.nook.common.web.exception.BusinessException}, 错误码见 XrayErrorCode.
 *   3. 实现类应做合理重连(gRPC channel 断线重连), 暴露给 service 的语义只有"成功 or 抛业务异常".
 *   4. 读类操作幂等; 写类操作上层会保证不重复调用, 实现端不需做去重.
 */
public interface XrayBackend {

    /** 该实例绑定的 resource_server.id。 */
    String serverId();

    /** 健康检查/鉴权探活：成功返回；失败抛 BusinessException(BACKEND_UNREACHABLE)。 */
    void verifyConnectivity();

    /** 列 inbound——给运营在后台关联"IP ↔ inbound"用。 */
    List<XrayInboundInfo> listInbounds();

    /**
     * 在指定 inbound 下加客户端。
     * 如果客户端已存在按 CLIENT_DUPLICATE 抛错；上层先 select DB 再 add，避免重复。
     */
    void addClient(XrayClientSpec spec);

    /** 删客户端；远端不存在时抛 CLIENT_NOT_FOUND。 */
    void delClient(XrayClientRef ref);

    /** 拉客户端流量与配额状态。 */
    XrayClientTraffic getClientTraffic(XrayClientRef ref);

    /** 把客户端流量计数清零(不删客户端)。 */
    void resetClientTraffic(XrayClientRef ref);
}
