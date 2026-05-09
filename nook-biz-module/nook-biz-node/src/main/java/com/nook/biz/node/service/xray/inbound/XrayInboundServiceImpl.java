package com.nook.biz.node.service.xray.inbound;

import com.nook.biz.node.controller.xray.inbound.vo.InboundSnapshotRespVO;
import com.nook.biz.node.convert.xray.inbound.XrayInboundConvert;
import com.nook.biz.node.framework.server.session.ServerSession;
import com.nook.biz.node.framework.server.session.ServerSessionManager;
import com.nook.biz.node.framework.xray.RemoteFiles;
import com.nook.biz.node.framework.ssh.dto.SshExecResult;
import com.nook.biz.node.framework.xray.inbound.config.InboundConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class XrayInboundServiceImpl implements XrayInboundService {

    private final ServerSessionManager sessionManager;

    @Override
    public List<InboundSnapshotRespVO> listInbounds(String serverId) {
        ServerSession session = sessionManager.acquire(serverId);
        // cat || echo '{}' 兜底: 远端配置文件不存在时返回空 inbound 列表而非报错
        SshExecResult r = session.ssh().exec(
                "cat " + RemoteFiles.CONFIG_PATH + " 2>/dev/null || echo '{}'",
                Duration.ofSeconds(15));
        return XrayInboundConvert.INSTANCE.convertList(InboundConfigParser.parseInbounds(r.stdout()));
    }
}
