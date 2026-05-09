package com.nook.biz.node.service.socks5;

import com.nook.biz.node.controller.socks5.vo.Socks5InstallReqVO;
import com.nook.biz.node.controller.socks5.vo.Socks5TestRespVO;

import java.util.function.Consumer;

/** SOCKS5 落地节点的运维操作: 部署 / 拨号测试 / 后续生命周期. */
public interface Socks5OpsService {

    /** 流式部署 (ad-hoc 凭据): 渲染模板 → 上传 → bash 执行 → stdout 每行回写. */
    void installAdHocStreaming(Socks5InstallReqVO reqVO, Consumer<String> lineSink);

    /** 拨号测试 IP 池条目的 SOCKS5; 通过该凭据访问 echo-IP 端点验证可达性 + 出网 IP. 失败也返回 success=false 不抛错. */
    Socks5TestRespVO testConnectivity(String ipId);
}
