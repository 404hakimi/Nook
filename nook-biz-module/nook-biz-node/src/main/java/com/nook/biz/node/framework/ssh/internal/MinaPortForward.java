package com.nook.biz.node.framework.ssh.internal;

import com.nook.biz.node.framework.ssh.PortForward;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;

/**
 * PortForward 的 MINA 实现, 包装 ExplicitPortForwardingTracker; 生命周期跟随宿主 ClientSession.
 *
 * @author nook
 */
class MinaPortForward implements PortForward {

    @SuppressWarnings("unused") // 持引用避免被 GC; tracker 自动跟随 ClientSession 关闭
    private final ExplicitPortForwardingTracker tracker;
    private final int localPort;

    MinaPortForward(ExplicitPortForwardingTracker tracker) {
        this.tracker = tracker;
        this.localPort = tracker.getBoundAddress().getPort();
    }

    @Override
    public int localPort() {
        return localPort;
    }
}
