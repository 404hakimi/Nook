package com.nook.biz.node.framework.xray.inbound.grpc;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.stats.GrpcConstants;
import com.nook.biz.node.framework.xray.inbound.config.InboundProtocolMapping;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.common.web.exception.BusinessException;
import com.xray.app.proxyman.command.AddUserOperation;
import com.xray.app.proxyman.command.AlterInboundRequest;
import com.xray.app.proxyman.command.HandlerServiceGrpc;
import com.xray.app.proxyman.command.RemoveUserOperation;
import com.xray.common.protocol.User;
import com.xray.common.serial.TypedMessage;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/** InboundGrpcClient 的实现; 走 HandlerService.AlterInbound. */
@Slf4j
@RequiredArgsConstructor
public class InboundGrpcClientImpl implements InboundGrpcClient {

    private final ServerCredentialDTO cred;
    private final ManagedChannel channel;

    @Override
    public void addUser(String inboundTag, InboundUserSpec spec) {
        InboundProtocolMapping protocol = InboundProtocolMapping.of(spec.protocol());
        TypedMessage account = protocol.buildAccountTypedMessage(spec);
        User user = User.newBuilder()
                .setLevel(0)
                .setEmail(StrUtil.blankToDefault(spec.email(), ""))
                .setAccount(account)
                .build();
        AddUserOperation op = AddUserOperation.newBuilder().setUser(user).build();
        try {
            handlerStub().alterInbound(AlterInboundRequest.newBuilder()
                    .setTag(inboundTag)
                    .setOperation(wrapOp(AddUserOperation.getDescriptor().getFullName(), op.toByteString()))
                    .build());
            log.info("[grpc] addUser server={} inbound={} email={} protocol={}",
                    cred.serverId(), inboundTag, spec.email(), protocol.getCode());
        } catch (StatusRuntimeException sre) {
            throw mapAlterInboundError(sre, "addUser", spec.email(),
                    GrpcConstants.DESC_USER_DUPLICATE, XrayErrorCode.CLIENT_DUPLICATE);
        }
    }

    @Override
    public void removeUser(String inboundTag, String email) {
        if (StrUtil.isBlank(email)) {
            // RemoveUserOperation 仅按 email 索引, 没 email 等价无法定位
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    cred.serverId(), "removeUser 缺 email");
        }
        RemoveUserOperation op = RemoveUserOperation.newBuilder().setEmail(email).build();
        try {
            handlerStub().alterInbound(AlterInboundRequest.newBuilder()
                    .setTag(inboundTag)
                    .setOperation(wrapOp(RemoveUserOperation.getDescriptor().getFullName(), op.toByteString()))
                    .build());
            log.info("[grpc] removeUser server={} inbound={} email={}",
                    cred.serverId(), inboundTag, email);
        } catch (StatusRuntimeException sre) {
            throw mapAlterInboundError(sre, "removeUser", email,
                    GrpcConstants.DESC_USER_NOT_FOUND, XrayErrorCode.CLIENT_NOT_FOUND);
        }
    }

    /**
     * AlterInbound Add/Remove 共享错误形态: code 多为 UNKNOWN, description 字符串描述具体原因.
     * 优先级: UNAVAILABLE/DEADLINE_EXCEEDED → BACKEND_UNREACHABLE; description 命中关键词 → 业务错误码;
     * 兜底 BACKEND_OPERATION_FAILED.
     */
    private BusinessException mapAlterInboundError(StatusRuntimeException sre,
                                                   String op,
                                                   String email,
                                                   String matchDescriptionKeyword,
                                                   XrayErrorCode mappedCode) {
        if (isTransient(sre.getStatus().getCode())) {
            log.warn("[grpc] {} UNAVAILABLE server={} email={}", op, cred.serverId(), email, sre);
            return new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, sre, cred.serverId());
        }
        String desc = StrUtil.blankToDefault(sre.getStatus().getDescription(), "");
        if (StrUtil.containsIgnoreCase(desc, matchDescriptionKeyword)) {
            return new BusinessException(mappedCode, email);
        }
        log.warn("[grpc] {} 失败 server={} email={} status={}", op, cred.serverId(), email, sre.getStatus());
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, sre,
                cred.serverId(), op + ": " + sre.getStatus());
    }

    private TypedMessage wrapOp(String typeFullName, com.google.protobuf.ByteString value) {
        return TypedMessage.newBuilder().setType(typeFullName).setValue(value).build();
    }

    private HandlerServiceGrpc.HandlerServiceBlockingStub handlerStub() {
        return HandlerServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(cred.backendTimeoutSeconds(), TimeUnit.SECONDS);
    }

    private static boolean isTransient(Status.Code code) {
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }
}
