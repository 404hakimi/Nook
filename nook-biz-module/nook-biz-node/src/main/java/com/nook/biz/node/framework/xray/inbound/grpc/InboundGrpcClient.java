package com.nook.biz.node.framework.xray.inbound.grpc;

import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;

/** Xray gRPC HandlerService 入站用户管理; addUser / removeUser. */
public interface InboundGrpcClient {

    /** 给指定 inbound 加 user; 远端 email 已存在抛 CLIENT_DUPLICATE. */
    void addUser(String inboundTag, InboundUserSpec spec);

    /** 从指定 inbound 删 user; 远端 email 不存在抛 CLIENT_NOT_FOUND. */
    void removeUser(String inboundTag, String email);
}
