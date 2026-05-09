package com.nook.biz.node.framework.xray.grpc;

import jakarta.annotation.Resource;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.PortForward;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
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
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Xray HandlerService gRPC 客户端: addUser / removeUser, 每次现建 ManagedChannel 不维护 channel 缓存.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayInboundClient {

    /** AlterInbound AddUser 时, email 已存在的 description 关键词. (Xray HandlerService 用 errors.New 抛错, 仅靠 description 区分.) */
    private static final String DESC_USER_DUPLICATE = "already exists";

    /** AlterInbound RemoveUser 时, email 不存在的 description 关键词. */
    private static final String DESC_USER_NOT_FOUND = "not found";

    @Resource
    private SshSessionManager sshSessionManager;
    @Resource
    private ResourceServerApi resourceServerApi;

    /**
     * 给指定 server 的 inbound 加 user, 远端 email 已存在抛 CLIENT_DUPLICATE.
     *
     * @param serverId   resource_server.id
     * @param inboundTag 远端 inbound tag
     * @param spec       user 协议规格 (UUID / email / flow / 限速 / 到期等)
     */
    public void addUser(String serverId, String inboundTag, InboundUserSpec spec) {
        ServerCredentialDTO cred = loadXrayCred(serverId);
        InboundProtocolMapping protocol = InboundProtocolMapping.of(spec.protocol());
        TypedMessage account = protocol.buildAccountTypedMessage(spec);
        User user = User.newBuilder()
                .setLevel(0)
                .setEmail(StrUtil.blankToDefault(spec.email(), ""))
                .setAccount(account)
                .build();
        AddUserOperation op = AddUserOperation.newBuilder().setUser(user).build();
        ManagedChannel ch = openChannel(serverId, cred);
        try {
            HandlerServiceGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(cred.backendTimeoutSeconds(), TimeUnit.SECONDS)
                    .alterInbound(AlterInboundRequest.newBuilder()
                            .setTag(inboundTag)
                            .setOperation(wrapOp(AddUserOperation.getDescriptor().getFullName(), op.toByteString()))
                            .build());
            log.info("[grpc] addUser server={} inbound={} email={} protocol={}",
                    serverId, inboundTag, spec.email(), protocol.getCode());
        } catch (StatusRuntimeException sre) {
            throw mapAlterInboundError(sre, serverId, "addUser", spec.email(),
                    DESC_USER_DUPLICATE, XrayErrorCode.CLIENT_DUPLICATE);
        } finally {
            ch.shutdownNow();
        }
    }

    /**
     * 从指定 server 的 inbound 删 user, 远端 email 不存在抛 CLIENT_NOT_FOUND.
     *
     * @param serverId   resource_server.id
     * @param inboundTag 远端 inbound tag
     * @param email      要删除的 user email
     */
    public void removeUser(String serverId, String inboundTag, String email) {
        if (StrUtil.isBlank(email)) {
            // RemoveUserOperation 仅按 email 索引, 没 email 等价无法定位
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    serverId, "removeUser 缺 email");
        }
        ServerCredentialDTO cred = loadXrayCred(serverId);
        RemoveUserOperation op = RemoveUserOperation.newBuilder().setEmail(email).build();
        ManagedChannel ch = openChannel(serverId, cred);
        try {
            HandlerServiceGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(cred.backendTimeoutSeconds(), TimeUnit.SECONDS)
                    .alterInbound(AlterInboundRequest.newBuilder()
                            .setTag(inboundTag)
                            .setOperation(wrapOp(RemoveUserOperation.getDescriptor().getFullName(), op.toByteString()))
                            .build());
            log.info("[grpc] removeUser server={} inbound={} email={}",
                    serverId, inboundTag, email);
        } catch (StatusRuntimeException sre) {
            throw mapAlterInboundError(sre, serverId, "removeUser", email,
                    DESC_USER_NOT_FOUND, XrayErrorCode.CLIENT_NOT_FOUND);
        } finally {
            ch.shutdownNow();
        }
    }

    /** 取 cred 并校验 xrayGrpcHost; gRPC 通道专属字段, SshSession 不要求. */
    private ServerCredentialDTO loadXrayCred(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        if (StrUtil.isBlank(cred.xrayGrpcHost())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, serverId);
        }
        return cred;
    }

    /** 拿 SshSession 上 cache 好的 PortForward, 现建 ManagedChannel; 调用方负责 finally shutdownNow. */
    private ManagedChannel openChannel(String serverId, ServerCredentialDTO cred) {
        SshSession ssh = sshSessionManager.acquire(serverId);
        PortForward fw = ssh.openLocalForward(cred.xrayGrpcHost(), cred.xrayGrpcPort());
        return ManagedChannelBuilder.forAddress("127.0.0.1", fw.localPort())
                .usePlaintext()
                .build();
    }

    /**
     * AlterInbound Add/Remove 共享错误映射: UNAVAILABLE/DEADLINE_EXCEEDED → BACKEND_UNREACHABLE;
     * description 命中关键词 → mappedCode; 兜底 BACKEND_OPERATION_FAILED.
     */
    private BusinessException mapAlterInboundError(StatusRuntimeException sre,
                                                   String serverId,
                                                   String op,
                                                   String email,
                                                   String matchDescriptionKeyword,
                                                   XrayErrorCode mappedCode) {
        if (isTransient(sre.getStatus().getCode())) {
            log.warn("[grpc] {} UNAVAILABLE server={} email={}", op, serverId, email, sre);
            return new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, sre, serverId);
        }
        String desc = StrUtil.blankToDefault(sre.getStatus().getDescription(), "");
        if (StrUtil.containsIgnoreCase(desc, matchDescriptionKeyword)) {
            return new BusinessException(mappedCode, email);
        }
        log.warn("[grpc] {} 失败 server={} email={} status={}", op, serverId, email, sre.getStatus());
        return new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, sre,
                serverId, op + ": " + sre.getStatus());
    }

    private static TypedMessage wrapOp(String typeFullName, com.google.protobuf.ByteString value) {
        return TypedMessage.newBuilder().setType(typeFullName).setValue(value).build();
    }

    private static boolean isTransient(Status.Code code) {
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }
}
