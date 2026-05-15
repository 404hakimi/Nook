package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpSocksInstallReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksSyncCredsReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.Socks5StatusRespVO;

import java.util.function.Consumer;

/**
 * SOCKS5 落地节点 Service 接口
 *
 * <p>负责 SOCKS5 节点部署 / 拨号测试.
 *
 * @author nook
 */
public interface ResourceIpSocksService {

    /**
     * 流式部署 SOCKS5 (ad-hoc 凭据), 渲染模板 → 上传 → bash 执行 → stdout 每行回写
     *
     * @param reqVO    部署入参 (含 ad-hoc SSH 凭据 + SOCKS5 服务参数)
     * @param lineSink 每行 stdout 的消费回调
     */
    void installSocks5(ResourceIpSocksInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 拨号测试 IP 池条目对应的 SOCKS5
     *
     * <p>走该凭据访问 echo-IP 端点验证可达性 + 出网 IP, 失败也返回 success=false.
     *
     * @param ipId  resource_ip_pool.id
     * @param reqVO 测试入参; reqVO 或其 echoUrl 为空时走后端默认 echo-IP 端点
     * @return Socks5TestRespVO; echoUrl / rawResponse 始终回填便于前端展示
     */
    ResourceIpSocksTestRespVO testSocks5(String ipId, ResourceIpSocksTestReqVO reqVO);

    /**
     * 流式同步 SOCKS5 凭据到 landing + 重建 fra-line 上对应 client 的 outbound (1:1 模型).
     *
     * <p>landing: SSH 走 ad-hoc cred (跟 install 同一套), 跑 update-dante-creds.sh 重写 conf + htpasswd + 视情况 restart.
     * fra-line: SSH 走 stored cred (resource_server), 仅 rmo + ado outbound, 不动 inbound.
     *
     * @param ipId     resource_ip_pool.id
     * @param reqVO    ad-hoc SSH 凭据 (host 来自 ip.ipAddress, 入参里只问 user/password/超时)
     * @param lineSink stdout 每行回调 (前端流式显示)
     */
    void syncSocks5Creds(String ipId, ResourceIpSocksSyncCredsReqVO reqVO, Consumer<String> lineSink);

    /**
     * 拉 SOCKS5 落地节点的 dante 服务运行状态; 走 IP 池条目里存储的 SSH 凭据 (ad-hoc).
     *
     * @param ipId resource_ip_pool.id; 必须是自部署且 SSH 凭据齐全
     * @return active / version / 启动时间 / 监听端口 / 开机自启
     */
    Socks5StatusRespVO getSocks5Status(String ipId);

    /**
     * 切 dante systemd 开机自启 (systemctl enable/disable), 同时把 DB.autostart_enabled 同步过去.
     *
     * @param ipId    resource_ip_pool.id
     * @param enabled true=enable / false=disable
     */
    void setSocks5Autostart(String ipId, boolean enabled);

    /**
     * 拉 dante journalctl 日志, 走 ServerProbe.readJournalLog 同一套实现 + 校验.
     *
     * @param ipId    resource_ip_pool.id
     * @param lines   行数 (默认 100, 上限 5000)
     * @param level   all / warning / err
     * @param keyword 关键词子串过滤 (大小写不敏感), 走白名单字符校验
     * @return 日志快照
     */
    ServiceLogRespVO getSocks5Log(String ipId, Integer lines, String level, String keyword);
}
